package org.moodle.core.html

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.moodle.core.network.MoodleUrl
import org.moodle.core.security.SecureCredentialStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface HtmlCookieStorage {
    fun read(accountId: String): String?
    fun write(accountId: String, serializedCookies: String)
}

@Singleton
class KeystoreHtmlCookieStorage @Inject constructor(
    private val credentials: SecureCredentialStore,
) : HtmlCookieStorage {
    override fun read(accountId: String): String? = credentials.cookies(accountId)
    override fun write(accountId: String, serializedCookies: String) = credentials.putCookies(accountId, serializedCookies)
}

private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean,
) {
    fun toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .expiresAt(expiresAt)
        .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
        .path(path)
        .apply {
            if (secure) secure()
            if (httpOnly) httpOnly()
        }
        .build()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            cookie.name,
            cookie.value,
            cookie.expiresAt,
            cookie.domain,
            cookie.path,
            cookie.secure,
            cookie.httpOnly,
            cookie.hostOnly,
            cookie.persistent,
        )
    }
}

private class EncryptedAccountCookieJar(
    private val accountId: String,
    private val baseUrl: String,
    private val storage: HtmlCookieStorage,
    private val gson: Gson,
) : CookieJar {
    private val type = object : TypeToken<List<StoredCookie>>() {}.type
    private val storedCookies = mutableListOf<Cookie>()

    init {
        val restored = storage.read(accountId)?.let { encoded ->
            runCatching { gson.fromJson<List<StoredCookie>>(encoded, type) }.getOrNull()
        }.orEmpty()
        storedCookies += restored.mapNotNull { runCatching { it.toCookie() }.getOrNull() }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!MoodleUrl.sameSite(baseUrl, url.toString())) return
        val now = System.currentTimeMillis()
        cookies.filter { it.secure }.forEach { incoming ->
            storedCookies.removeAll { current ->
                current.name == incoming.name && current.domain == incoming.domain && current.path == incoming.path
            }
            if (incoming.expiresAt > now) storedCookies += incoming
        }
        persist(now)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!MoodleUrl.sameSite(baseUrl, url.toString())) return emptyList()
        val now = System.currentTimeMillis()
        val removed = storedCookies.removeAll { it.expiresAt <= now }
        if (removed) persist(now)
        return storedCookies.filter { it.matches(url) && it.secure }
    }

    @Synchronized
    fun clear() {
        storedCookies.clear()
        storage.write(accountId, "[]")
    }

    private fun persist(now: Long) {
        storage.write(accountId, gson.toJson(storedCookies.filter { it.expiresAt > now }.map(StoredCookie::from)))
    }
}

class HtmlAccountClient internal constructor(
    val client: OkHttpClient,
    internal val clearCookies: () -> Unit,
)

@Singleton
class HtmlSessionClientFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val storage: HtmlCookieStorage,
    private val gson: Gson,
) {
    private val clients = ConcurrentHashMap<String, HtmlAccountClient>()

    fun client(accountId: String, baseUrl: String): HtmlAccountClient = clients.getOrPut(accountId) {
        val jar = EncryptedAccountCookieJar(accountId, baseUrl, storage, gson)
        HtmlAccountClient(
            baseClient.newBuilder()
                .cookieJar(jar)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
            jar::clear,
        )
    }

    fun clear(accountId: String, baseUrl: String) {
        clients.remove(accountId)?.clearCookies?.invoke()
            ?: EncryptedAccountCookieJar(accountId, baseUrl, storage, gson).clear()
    }
}
