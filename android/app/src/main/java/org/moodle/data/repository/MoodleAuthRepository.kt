package org.moodle.data.repository

import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.AuthState
import org.moodle.core.model.HtmlFeature
import org.moodle.core.model.MoodleError
import org.moodle.core.model.MoodlePublicConfig
import org.moodle.core.model.MoodleResult
import org.moodle.core.model.SiteAccount
import org.moodle.core.model.SiteCapabilities
import org.moodle.core.network.AjaxRequest
import org.moodle.core.html.HtmlMoodleDataSource
import org.moodle.core.html.HtmlMoodleException
import org.moodle.core.network.MoodleApi
import org.moodle.core.network.MoodleUrl
import org.moodle.core.security.PendingSso
import org.moodle.core.security.SecureCredentialStore
import org.moodle.core.security.SsoProtocol
import org.moodle.core.security.SsoSessionStore
import org.moodle.data.local.AppPreferences
import org.moodle.data.local.MoodleDao
import org.moodle.data.local.toDomain
import org.moodle.data.local.toEntity
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface MoodleAuthRepository {
    val accounts: Flow<List<SiteAccount>>
    val activeAccount: Flow<SiteAccount?>
    suspend fun inspectSite(inputUrl: String): MoodleResult<MoodlePublicConfig>
    suspend fun login(config: MoodlePublicConfig, username: String, password: String): MoodleResult<SiteAccount>
    suspend fun reauthenticate(accountId: String, username: String, password: String): MoodleResult<SiteAccount>
    fun beginSso(config: MoodlePublicConfig): MoodleResult<String>
    suspend fun completeSso(callback: Uri): MoodleResult<SiteAccount>
    suspend fun activate(accountId: String)
    suspend fun remove(accountId: String)
}

@Singleton
class DefaultMoodleAuthRepository @Inject constructor(
    private val api: MoodleApi,
    private val dao: MoodleDao,
    private val tokenStore: SecureCredentialStore,
    private val ssoSessionStore: SsoSessionStore,
    private val appPreferences: AppPreferences,
    private val htmlDataSource: HtmlMoodleDataSource,
    private val gson: Gson,
) : MoodleAuthRepository {
    override val accounts: Flow<List<SiteAccount>> = dao.observeAccounts().map { rows ->
        rows.map { it.toDomain(gson) }
    }
    override val activeAccount: Flow<SiteAccount?> = combine(accounts, appPreferences.activeAccountId) { rows, activeId ->
        rows.firstOrNull { it.id == activeId } ?: rows.firstOrNull { it.isActive }
    }

    override suspend fun inspectSite(inputUrl: String): MoodleResult<MoodlePublicConfig> = safely {
        val baseUrl = MoodleUrl.normalize(inputUrl)
        val language = Locale.getDefault().language.take(8).ifBlank { "en" }
        val response = api.publicConfig(
            "$baseUrl/lib/ajax/service-nologin.php?info=tool_mobile_get_public_config&lang=$language",
            listOf(AjaxRequest(methodname = "tool_mobile_get_public_config")),
        ).firstOrNull() ?: error("The Moodle site returned an empty configuration")
        if (response.error) {
            val exception = response.exception
            throw MoodleRepositoryException(
                exception?.errorcode ?: "public_config_failed",
                exception?.message ?: "The Moodle public configuration could not be read",
            )
        }
        val config = response.data ?: error("The Moodle site returned an empty configuration")
        val canonical = MoodleUrl.normalize(config.httpswwwroot.ifBlank { config.wwwroot.ifBlank { baseUrl } })
        require(MoodleUrl.sameSite(baseUrl, canonical)) {
            "The Moodle canonical URL must stay on the entered site"
        }
        MoodlePublicConfig(
            canonicalUrl = canonical,
            siteName = config.sitename.ifBlank { canonical },
            mobileWebServiceEnabled = config.enablemobilewebservice == 1,
            loginType = config.typeoflogin,
            launchUrl = config.launchurl,
            showLoginForm = config.showloginform != 0,
        )
    }

    override suspend fun login(
        config: MoodlePublicConfig,
        username: String,
        password: String,
    ): MoodleResult<SiteAccount> = safely {
        if (config.connectionMode == ConnectionMode.NativeHtml) {
            return@safely createHtmlAccount(config, username, password)
        }
        val response = api.loginToken(
            "${config.canonicalUrl}/login/token.php?lang=${Locale.getDefault().language}",
            username.trim(),
            password,
        )
        val token = response.token ?: throw MoodleRepositoryException(
            response.errorcode ?: "login_failed",
            response.error ?: "Login failed",
        )
        createNativeAccount(config, token, response.privateToken, username.trim())
    }

    override suspend fun reauthenticate(
        accountId: String,
        username: String,
        password: String,
    ): MoodleResult<SiteAccount> = safely {
        val existing = dao.getAccount(accountId)?.toDomain(gson)
            ?: throw MoodleRepositoryException("account_missing", "The Moodle account no longer exists")
        require(existing.connectionMode == ConnectionMode.NativeHtml) { "Only HTML accounts use this sign-in flow" }
        val result = htmlDataSource.login(
            accountId,
            MoodlePublicConfig(existing.baseUrl, existing.siteName, false, 1, null, true),
            username.trim(),
            password,
        )
        val candidate = existing.copy(
            username = username.trim(),
            userId = result.identity.userId ?: existing.userId,
            fullName = result.identity.fullName ?: existing.fullName,
            siteName = result.identity.siteName,
            capabilities = SiteCapabilities(htmlFeatures = result.identity.features),
            authState = AuthState.Authenticated,
            moodleVersion = result.identity.moodleVersion,
            themeFamily = result.identity.themeFamily,
        )
        val refreshed = candidate.copy(capabilities = SiteCapabilities(htmlFeatures = validatedHtmlFeatures(candidate)))
        dao.upsertAccount(refreshed.toEntity(gson))
        activate(refreshed.id)
        refreshed
    }

    override fun beginSso(config: MoodlePublicConfig): MoodleResult<String> = runCatching {
        require(config.connectionMode == ConnectionMode.NativeApi) { "Mobile web services are disabled" }
        val passport = SsoProtocol.createPassport()
        ssoSessionStore.save(PendingSso(config.canonicalUrl, passport, nowEpochSeconds()))
        SsoProtocol.launchUrl(config.canonicalUrl, config.launchUrl, passport)
    }.fold(
        onSuccess = { MoodleResult.Success(it) },
        onFailure = { MoodleResult.Failure(it.asMoodleError()) },
    )

    override suspend fun completeSso(callback: Uri): MoodleResult<SiteAccount> = safely {
        val pending = ssoSessionStore.consume() ?: throw MoodleRepositoryException(
            "missing_sso_request",
            "No matching SSO request is pending",
        )
        val credentials = SsoProtocol.validateCallback(callback, pending, nowEpochSeconds())
        val inspected = inspectSite(credentials.baseUrl)
        val config = (inspected as? MoodleResult.Success)?.value ?: throw MoodleRepositoryException(
            "site_check_failed",
            (inspected as MoodleResult.Failure).error.message,
        )
        createNativeAccount(config, credentials.token, credentials.privateToken, null)
    }

    override suspend fun activate(accountId: String) {
        dao.setActiveAccount(accountId)
        appPreferences.setActiveAccountId(accountId)
    }

    override suspend fun remove(accountId: String) {
        dao.getAccount(accountId)?.toDomain(gson)?.takeIf { it.connectionMode == ConnectionMode.NativeHtml }
            ?.let { htmlDataSource.clearSession(accountId, it.baseUrl) }
        tokenStore.delete(accountId)
        appPreferences.clearActiveAccountIfMatches(accountId)
        dao.deleteAccount(accountId)
    }

    private suspend fun createNativeAccount(
        config: MoodlePublicConfig,
        token: String,
        privateToken: String?,
        enteredUsername: String?,
    ): SiteAccount {
        val siteInfo = callRest(
            config.canonicalUrl,
            token,
            "core_webservice_get_site_info",
            emptyMap(),
        ).asJsonObject
        val functions = siteInfo.getAsJsonArray("functions")
            ?.mapNotNull { it.asJsonObject.get("name")?.asString }
            ?.toSet()
            .orEmpty()
        val account = SiteAccount(
            id = UUID.randomUUID().toString(),
            baseUrl = config.canonicalUrl,
            siteName = siteInfo.string("sitename") ?: config.siteName,
            username = siteInfo.string("username") ?: enteredUsername,
            userId = siteInfo.long("userid"),
            fullName = siteInfo.string("fullname"),
            connectionMode = ConnectionMode.NativeApi,
            capabilities = SiteCapabilities(functions),
            isActive = true,
        )
        tokenStore.put(account.id, token, privateToken)
        dao.upsertAccount(account.toEntity(gson))
        activate(account.id)
        return account
    }

    private suspend fun createHtmlAccount(
        config: MoodlePublicConfig,
        username: String,
        password: String,
    ): SiteAccount {
        val accountId = UUID.randomUUID().toString()
        val result = try {
            htmlDataSource.login(accountId, config, username.trim(), password)
        } catch (error: Throwable) {
            htmlDataSource.clearSession(accountId, config.canonicalUrl)
            throw error
        }
        val candidate = SiteAccount(
            id = accountId,
            baseUrl = config.canonicalUrl,
            siteName = result.identity.siteName,
            username = username.trim(),
            userId = result.identity.userId,
            fullName = result.identity.fullName,
            connectionMode = ConnectionMode.NativeHtml,
            capabilities = SiteCapabilities(htmlFeatures = result.identity.features),
            authState = AuthState.Authenticated,
            moodleVersion = result.identity.moodleVersion,
            themeFamily = result.identity.themeFamily,
            isActive = true,
        )
        val account = candidate.copy(capabilities = SiteCapabilities(htmlFeatures = validatedHtmlFeatures(candidate)))
        dao.upsertAccount(account.toEntity(gson))
        activate(account.id)
        return account
    }

    private suspend fun validatedHtmlFeatures(account: SiteAccount): Set<HtmlFeature> {
        val features = account.capabilities.htmlFeatures
        if (account.userId == null) return features - MESSAGE_HTML_FEATURES
        val messagesAvailable = runCatching { htmlDataSource.conversations(account, 0, 1) }.isSuccess
        return if (messagesAvailable) features else features - MESSAGE_HTML_FEATURES
    }

    private suspend fun callRest(
        baseUrl: String,
        token: String,
        function: String,
        parameters: Map<String, String>,
    ) = api.restCall(
        "$baseUrl/webservice/rest/server.php",
        mapOf(
            "wstoken" to token,
            "wsfunction" to function,
            "moodlewsrestformat" to "json",
        ) + parameters,
    ).also { element ->
        if (element.isJsonObject && element.asJsonObject.has("exception")) {
            val error = element.asJsonObject
            throw MoodleRepositoryException(
                error.string("errorcode") ?: "webservice_error",
                error.string("message") ?: "Moodle web service error",
            )
        }
    }
}

private val MESSAGE_HTML_FEATURES = setOf(
    HtmlFeature.MessagesRead,
    HtmlFeature.MessagesSearch,
    HtmlFeature.MessagesSend,
    HtmlFeature.MessagesMarkRead,
)

class MoodleRepositoryException(val code: String, override val message: String) : Exception(message)

internal inline fun <T> safely(block: () -> T): MoodleResult<T> = runCatching(block).fold(
    onSuccess = { MoodleResult.Success(it) },
    onFailure = { MoodleResult.Failure(it.asMoodleError()) },
)

internal fun Throwable.asMoodleError(): MoodleError = when (this) {
    is MoodleRepositoryException -> MoodleError(code, message, code !in setOf("invalidtoken", "access_control_exception"))
    is HtmlMoodleException -> MoodleError(code, message, code !in setOf("invalid_credentials", "session_expired"))
    is IllegalArgumentException -> MoodleError("invalid_input", message ?: "Invalid input", false)
    else -> MoodleError("network_error", message ?: "Could not connect to Moodle")
}

internal fun com.google.gson.JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

internal fun com.google.gson.JsonObject.long(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.runCatching { asLong }?.getOrNull()

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
