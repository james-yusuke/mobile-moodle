package org.moodle.core.html

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import org.moodle.core.model.ConversationType
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleConversationMember
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.MoodleMessageUser
import org.moodle.core.model.MoodlePublicConfig
import org.moodle.core.model.MoodleSection
import org.moodle.core.model.MoodleModuleContent
import org.moodle.core.model.SiteAccount
import org.moodle.core.network.MoodleUrl
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class HtmlLoginResult(val identity: HtmlSiteIdentity)

class HtmlMoodleException(val code: String, override val message: String) : Exception(message)

interface HtmlMoodleDataSource {
    suspend fun login(accountId: String, config: MoodlePublicConfig, username: String, password: String): HtmlLoginResult
    suspend fun courses(account: SiteAccount): List<MoodleCourse>
    suspend fun sections(account: SiteAccount, courseId: Long): List<MoodleSection>
    suspend fun grades(account: SiteAccount, courses: List<MoodleCourse>): List<MoodleGrade>
    suspend fun events(account: SiteAccount): List<MoodleCalendarEvent>
    suspend fun notifications(account: SiteAccount): List<MoodleNotification>
    suspend fun conversations(account: SiteAccount, offset: Int = 0, limit: Int = 30): List<MoodleConversation>
    suspend fun conversationMessages(
        account: SiteAccount,
        conversationId: Long,
        offset: Int = 0,
        limit: Int = 50,
    ): List<MoodleMessage>
    suspend fun searchMessageUsers(account: SiteAccount, query: String, limit: Int = 30): List<MoodleMessageUser>
    suspend fun sendConversationMessage(account: SiteAccount, conversationId: Long, text: String): MoodleMessage?
    suspend fun sendDirectMessage(account: SiteAccount, userId: Long, text: String): Long
    suspend fun markConversationRead(account: SiteAccount, conversationId: Long)
    suspend fun moduleContent(account: SiteAccount, title: String, url: String): MoodleModuleContent
    suspend fun download(account: SiteAccount, url: String, destination: File)
    fun clearSession(accountId: String, baseUrl: String)
}

@Singleton
class DefaultHtmlMoodleDataSource @Inject constructor(
    private val sessions: HtmlSessionClientFactory,
    private val parser: MoodleHtmlParser,
    private val gson: Gson,
) : HtmlMoodleDataSource {
    private val messageSessions = ConcurrentHashMap<String, CachedHtmlMessageSession>()

    override suspend fun login(
        accountId: String,
        config: MoodlePublicConfig,
        username: String,
        password: String,
    ): HtmlLoginResult = withContext(Dispatchers.IO) {
        val loginUrl = "${config.canonicalUrl}/login/index.php"
        val loginPage = getPage(accountId, config.canonicalUrl, loginUrl, allowLoginPage = true)
        val form = parser.loginForm(loginPage.document)
            ?: throw HtmlMoodleException("login_form_missing", "This Moodle login form is not supported")
        requireSameSite(config.canonicalUrl, form.actionUrl, "login form")
        val body = FormBody.Builder().apply {
            form.hiddenFields
                .filterKeys { it !in setOf("username", "password") }
                .forEach { (name, value) -> add(name, value) }
            add("username", username)
            add("password", password)
        }.build()
        val response = execute(
            accountId,
            config.canonicalUrl,
            Request.Builder().url(form.actionUrl).post(body).header("Accept", "text/html").build(),
        )
        val document = parser.document(response.body, response.url)
        if (parser.isLoginPage(document)) {
            clearSession(accountId, config.canonicalUrl)
            val message = document.selectFirst(".loginerrors, .alert-danger, [role=alert]")?.text()?.trim()
                ?: "The username or password was not accepted"
            throw HtmlMoodleException("invalid_credentials", message)
        }
        val identity = parser.identity(document, config.siteName)
        if (identity.userId != null && !identity.sesskey.isNullOrBlank()) {
            messageSessions[accountId] = CachedHtmlMessageSession(
                HtmlMessageSession(identity.userId, identity.sesskey),
                System.currentTimeMillis(),
            )
        }
        HtmlLoginResult(identity)
    }

    override suspend fun courses(account: SiteAccount): List<MoodleCourse> = withContext(Dispatchers.IO) {
        val parsed = runCatching {
            parser.courses(getPage(account.id, account.baseUrl, "${account.baseUrl}/my/courses.php").document)
        }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) return@withContext parsed
        val dashboard = getPage(account.id, account.baseUrl, "${account.baseUrl}/my/")
        parser.courses(dashboard.document).ifEmpty {
            internalAjaxCourses(account, parser.identity(dashboard.document, account.siteName).sesskey)
        }
    }

    override suspend fun sections(account: SiteAccount, courseId: Long): List<MoodleSection> = withContext(Dispatchers.IO) {
        val page = getPage(account.id, account.baseUrl, "${account.baseUrl}/course/view.php?id=$courseId")
        parser.courseSections(page.document, courseId)
    }

    override suspend fun grades(account: SiteAccount, courses: List<MoodleCourse>): List<MoodleGrade> =
        withContext(Dispatchers.IO) {
            courses.flatMap { course ->
                runCatching {
                    val page = getPage(
                        account.id,
                        account.baseUrl,
                        "${account.baseUrl}/grade/report/user/index.php?id=${course.id}",
                    )
                    parser.grades(page.document, course.id)
                }.getOrDefault(emptyList())
            }
        }

    override suspend fun events(account: SiteAccount): List<MoodleCalendarEvent> = withContext(Dispatchers.IO) {
        val page = getPage(account.id, account.baseUrl, "${account.baseUrl}/calendar/view.php?view=upcoming")
        parser.events(page.document)
    }

    override suspend fun notifications(account: SiteAccount): List<MoodleNotification> = withContext(Dispatchers.IO) {
        val page = getPage(
            account.id,
            account.baseUrl,
            "${account.baseUrl}/message/output/popup/notifications.php",
        )
        parser.notifications(page.document)
    }

    override suspend fun conversations(
        account: SiteAccount,
        offset: Int,
        limit: Int,
    ): List<MoodleConversation> = withContext(Dispatchers.IO) {
        val session = sessionContext(account)
        val data = ajax(
            account,
            session.sesskey,
            "core_message_get_conversations",
            JsonObject().apply {
                addProperty("userid", session.userId)
                addProperty("limitfrom", offset.coerceAtLeast(0))
                addProperty("limitnum", limit.coerceIn(1, 50))
            },
        )
        data.objectOrNull()?.array("conversations").orEmpty().mapNotNull { element ->
            parseConversation(element.objectOrNull() ?: return@mapNotNull null, session.userId)
        }
    }

    override suspend fun conversationMessages(
        account: SiteAccount,
        conversationId: Long,
        offset: Int,
        limit: Int,
    ): List<MoodleMessage> = withContext(Dispatchers.IO) {
        val session = sessionContext(account)
        val data = ajax(
            account,
            session.sesskey,
            "core_message_get_conversation_messages",
            JsonObject().apply {
                addProperty("currentuserid", session.userId)
                addProperty("convid", conversationId)
                addProperty("limitfrom", offset.coerceAtLeast(0))
                addProperty("limitnum", limit.coerceIn(1, 50))
                addProperty("newest", true)
                addProperty("timefrom", 0)
            },
        )
        data.objectOrNull()?.array("messages").orEmpty().mapNotNull { element ->
            parseMessage(element.objectOrNull() ?: return@mapNotNull null, conversationId, session.userId)
        }.sortedWith(compareBy<MoodleMessage> { it.createdAt }.thenBy { it.id })
    }

    override suspend fun searchMessageUsers(
        account: SiteAccount,
        query: String,
        limit: Int,
    ): List<MoodleMessageUser> = withContext(Dispatchers.IO) {
        val session = sessionContext(account)
        val data = ajax(
            account,
            session.sesskey,
            "core_message_message_search_users",
            JsonObject().apply {
                addProperty("userid", session.userId)
                addProperty("search", query.trim())
                addProperty("limitfrom", 0)
                addProperty("limitnum", limit.coerceIn(1, 50))
            },
        )
        buildList { collectMessageUsers(data, session.userId, this) }
            .distinctBy { it.id }
            .filter { it.canMessage }
            .take(limit)
    }

    override suspend fun sendConversationMessage(
        account: SiteAccount,
        conversationId: Long,
        text: String,
    ): MoodleMessage? = withContext(Dispatchers.IO) {
        val session = sessionContext(account)
        val data = ajax(
            account,
            session.sesskey,
            "core_message_send_messages_to_conversation",
            JsonObject().apply {
                addProperty("conversationid", conversationId)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("text", text)
                        addProperty("textformat", 0)
                    })
                })
            },
        )
        val message = when {
            data.isJsonArray -> data.asJsonArray.firstOrNull()
            else -> data.objectOrNull()?.array("messages")?.firstOrNull()
        }?.objectOrNull()
        message?.let { parseMessage(it, conversationId, session.userId) }
    }

    override suspend fun sendDirectMessage(account: SiteAccount, userId: Long, text: String): Long =
        withContext(Dispatchers.IO) {
            val session = sessionContext(account)
            val sent = ajax(
                account,
                session.sesskey,
                "core_message_send_instant_messages",
                JsonObject().apply {
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("touserid", userId)
                            addProperty("text", text)
                            addProperty("textformat", 0)
                            addProperty("clientmsgid", "android-${System.currentTimeMillis()}")
                        })
                    })
                },
            )
            val returnedConversationId = when {
                sent.isJsonArray -> sent.asJsonArray.firstOrNull()?.objectOrNull()?.long("conversationid")
                else -> sent.objectOrNull()?.long("conversationid")
            }
            if (returnedConversationId != null && returnedConversationId > 0) return@withContext returnedConversationId

            val conversationId = runCatching {
                ajax(
                    account,
                    session.sesskey,
                    "core_message_get_conversation_between_users",
                    JsonObject().apply {
                        addProperty("userid", session.userId)
                        addProperty("otheruserid", userId)
                        addProperty("includecontactrequests", false)
                        addProperty("includeprivacyinfo", false)
                        addProperty("memberlimit", 0)
                        addProperty("memberoffset", 0)
                        addProperty("messagelimit", 1)
                        addProperty("messageoffset", 0)
                        addProperty("newestmessagesfirst", true)
                    },
                ).objectOrNull()?.long("id")
            }.getOrNull()
            conversationId ?: conversations(account, 0, 30)
                .firstOrNull { conversation -> conversation.members.any { it.id == userId } }
                ?.id
                ?: throw HtmlMoodleException(
                    "message_sent_refresh_needed",
                    "The message was sent, but Moodle did not return the conversation. Refresh messages to continue.",
                )
        }

    override suspend fun markConversationRead(account: SiteAccount, conversationId: Long) =
        withContext(Dispatchers.IO) {
            val session = sessionContext(account)
            ajax(
                account,
                session.sesskey,
                "core_message_mark_all_conversation_messages_as_read",
                JsonObject().apply {
                    addProperty("userid", session.userId)
                    addProperty("conversationid", conversationId)
                },
            )
            Unit
        }

    override suspend fun moduleContent(
        account: SiteAccount,
        title: String,
        url: String,
    ): MoodleModuleContent = withContext(Dispatchers.IO) {
        requireSameSite(account.baseUrl, url, "module")
        val page = getPage(account.id, account.baseUrl, url)
        parser.moduleContent(page.document, title, page.url)
    }

    override suspend fun download(account: SiteAccount, url: String, destination: File) = withContext(Dispatchers.IO) {
        requireSameSite(account.baseUrl, url, "download")
        downloadToFile(account.id, account.baseUrl, url, destination)
        Unit
    }

    override fun clearSession(accountId: String, baseUrl: String) {
        messageSessions.remove(accountId)
        sessions.clear(accountId, baseUrl)
    }

    private fun internalAjaxCourses(account: SiteAccount, sesskey: String?): List<MoodleCourse> {
        if (sesskey.isNullOrBlank()) return emptyList()
        val args = JsonObject().apply {
            addProperty("offset", 0)
            addProperty("limit", 0)
            addProperty("classification", "all")
            addProperty("sort", "fullname")
            addProperty("customfieldname", "")
            addProperty("customfieldvalue", "")
        }
        val call = JsonObject().apply {
            addProperty("index", 0)
            addProperty("methodname", "core_course_get_enrolled_courses_by_timeline_classification")
            add("args", args)
        }
        val endpoint = "${account.baseUrl}/lib/ajax/service.php?sesskey=$sesskey&info=core_course_get_enrolled_courses_by_timeline_classification"
        val response = execute(
            account.id,
            account.baseUrl,
            Request.Builder()
                .url(endpoint)
                .post(JsonArray().apply { add(call) }.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build(),
        )
        val root = runCatching { gson.fromJson(response.body, JsonArray::class.java) }.getOrNull() ?: return emptyList()
        val data = root.firstOrNull()?.asJsonObject?.getAsJsonObject("data") ?: return emptyList()
        return data.getAsJsonArray("courses")?.mapNotNull { item ->
            val value = item.asJsonObject
            val id = value.get("id")?.asLong ?: return@mapNotNull null
            val fullName = value.get("fullname")?.asString.orEmpty()
            MoodleCourse(
                id,
                value.get("shortname")?.asString ?: fullName,
                fullName,
                parser.sanitize(value.get("summary")?.asString.orEmpty()),
                value.get("startdate")?.asLong,
                value.get("enddate")?.asLong?.takeIf { it > 0 },
            )
        }.orEmpty()
    }

    private fun sessionContext(account: SiteAccount): HtmlMessageSession {
        messageSessions[account.id]?.takeIf {
            System.currentTimeMillis() - it.cachedAtMillis < MESSAGE_SESSION_CACHE_MILLIS
        }?.let { return it.session }
        val dashboard = getPage(account.id, account.baseUrl, "${account.baseUrl}/my/")
        val identity = parser.identity(dashboard.document, account.siteName)
        val sesskey = identity.sesskey
            ?: throw HtmlMoodleException("session_context_missing", "Moodle did not provide a session key")
        val userId = account.userId ?: identity.userId
            ?: throw HtmlMoodleException("session_context_missing", "Moodle did not provide a user id")
        return HtmlMessageSession(userId, sesskey).also { session ->
            messageSessions[account.id] = CachedHtmlMessageSession(session, System.currentTimeMillis())
        }
    }

    private fun ajax(account: SiteAccount, sesskey: String, method: String, args: JsonObject): JsonElement {
        val call = JsonObject().apply {
            addProperty("index", 0)
            addProperty("methodname", method)
            add("args", args)
        }
        val endpoint = "${account.baseUrl}/lib/ajax/service.php".toHttpUrl().newBuilder()
            .addQueryParameter("sesskey", sesskey)
            .addQueryParameter("info", method)
            .build()
        val response = execute(
            account.id,
            account.baseUrl,
            Request.Builder()
                .url(endpoint)
                .post(JsonArray().apply { add(call) }.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build(),
        )
        val root = runCatching { gson.fromJson(response.body, JsonArray::class.java) }.getOrNull()
            ?: throw HtmlMoodleException("invalid_ajax_response", "Moodle returned an invalid message response")
        val item = root.firstOrNull()?.objectOrNull()
            ?: throw HtmlMoodleException("invalid_ajax_response", "Moodle returned an empty message response")
        if (item.bool("error") == true || item.has("exception")) {
            val exception = item.get("exception")?.objectOrNull()
            val code = exception?.string("errorcode") ?: item.string("errorcode") ?: "ajax_error"
            val message = exception?.string("message") ?: item.string("message") ?: "Moodle message request failed"
            if (code in setOf("invalidsesskey", "requireloginerror", "sessionipnomatch")) {
                messageSessions.remove(account.id)
                throw HtmlMoodleException("session_expired", "Please sign in again")
            }
            throw HtmlMoodleException(code, message)
        }
        return item.get("data") ?: com.google.gson.JsonNull.INSTANCE
    }

    private fun parseConversation(value: JsonObject, currentUserId: Long): MoodleConversation? {
        val id = value.long("id") ?: return null
        val members = value.array("members").mapNotNull { memberElement ->
            val member = memberElement.objectOrNull() ?: return@mapNotNull null
            val memberId = member.long("id") ?: return@mapNotNull null
            MoodleConversationMember(
                memberId,
                member.string("fullname") ?: member.string("name") ?: "Moodle user",
                memberId == currentUserId,
                member.bool("canmessage") ?: true,
            )
        }
        val type = when (value.int("type")) {
            1 -> ConversationType.Individual
            2 -> ConversationType.Group
            3 -> ConversationType.Self
            else -> ConversationType.Unknown
        }
        val lastMessage = value.array("messages").firstOrNull()?.objectOrNull()
        val safePreview = parser.sanitizeMessage(
            lastMessage?.string("text")
                ?: lastMessage?.string("smallmessage")
                ?: value.string("smallmessage")
                ?: "",
        )
        val name = value.string("name").orEmpty().ifBlank {
            members.filterNot { it.isCurrentUser }.joinToString(", ") { it.fullName }
        }.ifBlank { accountSafeConversationName(type) }
        return MoodleConversation(
            id,
            type,
            name,
            members,
            Jsoup.parse(safePreview).text(),
            value.long("timemodified") ?: lastMessage?.long("timecreated") ?: 0,
            value.int("unreadcount") ?: 0,
            value.bool("isfavourite") ?: false,
            members.any { !it.isCurrentUser && it.canMessage } || type == ConversationType.Group,
        )
    }

    private fun parseMessage(value: JsonObject, conversationId: Long, currentUserId: Long): MoodleMessage? {
        val id = value.long("id") ?: value.long("msgid") ?: return null
        val senderId = value.long("useridfrom") ?: value.long("userid") ?: 0
        val safeHtml = parser.sanitizeMessage(
            value.string("text") ?: value.string("fullmessagehtml") ?: value.string("fullmessage") ?: "",
        )
        return MoodleMessage(
            id,
            value.long("conversationid") ?: conversationId,
            senderId,
            value.string("userfullname") ?: value.string("sendername") ?: "Moodle user",
            Jsoup.parse(safeHtml).text(),
            safeHtml,
            value.long("timecreated") ?: System.currentTimeMillis() / 1_000L,
            senderId == currentUserId,
            (value.long("timeread") ?: 0) > 0 || value.bool("isread") == true || senderId == currentUserId,
        )
    }

    private fun collectMessageUsers(element: JsonElement, currentUserId: Long, result: MutableList<MoodleMessageUser>) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectMessageUsers(it, currentUserId, result) }
            element.isJsonObject -> {
                val value = element.asJsonObject
                val id = value.long("id") ?: value.long("userid")
                val name = value.string("fullname") ?: value.string("name")
                if (id != null && id != currentUserId && !name.isNullOrBlank()) {
                    result += MoodleMessageUser(id, name, value.bool("canmessage") ?: true)
                } else {
                    value.entrySet().forEach { (_, child) -> collectMessageUsers(child, currentUserId, result) }
                }
            }
        }
    }

    private fun accountSafeConversationName(type: ConversationType): String = when (type) {
        ConversationType.Group -> "Group conversation"
        ConversationType.Self -> "Personal space"
        else -> "Conversation"
    }

    private fun getPage(
        accountId: String,
        baseUrl: String,
        url: String,
        allowLoginPage: Boolean = false,
    ): HtmlPage {
        requireSameSite(baseUrl, url, "page")
        val response = execute(
            accountId,
            baseUrl,
            Request.Builder().url(url).get().header("Accept", "text/html").build(),
        )
        val document = parser.document(response.body, response.url)
        if (!allowLoginPage && parser.isLoginPage(document)) {
            throw HtmlMoodleException("session_expired", "Please sign in again")
        }
        return HtmlPage(response.url, document)
    }

    private fun execute(
        accountId: String,
        baseUrl: String,
        initialRequest: Request,
    ): HtmlResponse {
        var request = initialRequest
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requireSameSite(baseUrl, request.url.toString(), "request")
            sessions.client(accountId, baseUrl).client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw HtmlMoodleException("too_many_redirects", "Moodle redirected too many times")
                    }
                    val location = response.header("Location")
                        ?: throw HtmlMoodleException("invalid_redirect", "Moodle returned an invalid redirect")
                    val nextUrl = request.url.resolve(location)
                        ?: throw HtmlMoodleException("invalid_redirect", "Moodle returned an invalid redirect")
                    requireSameSite(baseUrl, nextUrl.toString(), "redirect")
                    request = if (response.code in setOf(307, 308)) {
                        request.newBuilder().url(nextUrl).build()
                    } else {
                        request.newBuilder().url(nextUrl).get().removeHeader("Content-Type").build()
                    }
                    return@use
                }
                if (!response.isSuccessful) {
                    throw HtmlMoodleException("http_${response.code}", "Moodle returned HTTP ${response.code}")
                }
                val contentLength = response.body.contentLength()
                if (contentLength > MAX_HTML_BYTES) {
                    throw HtmlMoodleException("page_too_large", "The Moodle page is too large to display")
                }
                val bytes = response.body.bytes()
                if (bytes.size > MAX_HTML_BYTES) {
                    throw HtmlMoodleException("page_too_large", "The Moodle page is too large to display")
                }
                return HtmlResponse(response.request.url.toString(), bytes.toString(Charsets.UTF_8))
            }
        }
        error("Unreachable")
    }

    private fun downloadToFile(accountId: String, baseUrl: String, url: String, destination: File) {
        var request = Request.Builder().url(url).get().header("Accept", "*/*").build()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requireSameSite(baseUrl, request.url.toString(), "download")
            sessions.client(accountId, baseUrl).client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw HtmlMoodleException("too_many_redirects", "Moodle redirected too many times")
                    }
                    val nextUrl = response.header("Location")?.let(request.url::resolve)
                        ?: throw HtmlMoodleException("invalid_redirect", "Moodle returned an invalid redirect")
                    requireSameSite(baseUrl, nextUrl.toString(), "download redirect")
                    request = request.newBuilder().url(nextUrl).get().build()
                    return@use
                }
                if (!response.isSuccessful) {
                    throw HtmlMoodleException("download_failed", "Download failed (${response.code})")
                }
                response.body.byteStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                return
            }
        }
    }

    private fun requireSameSite(baseUrl: String, url: String, purpose: String) {
        if (!MoodleUrl.sameSite(baseUrl, url)) {
            throw HtmlMoodleException("cross_origin_blocked", "Blocked a cross-origin $purpose")
        }
    }

    private data class HtmlPage(val url: String, val document: Document)
    private data class HtmlResponse(val url: String, val body: String)
    private data class HtmlMessageSession(val userId: Long, val sesskey: String)
    private data class CachedHtmlMessageSession(val session: HtmlMessageSession, val cachedAtMillis: Long)

    private companion object {
        const val MAX_REDIRECTS = 8
        const val MAX_HTML_BYTES = 10 * 1024 * 1024
        const val MESSAGE_SESSION_CACHE_MILLIS = 5 * 60 * 1_000L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonElement.objectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.runCatching { asString }?.getOrNull()
private fun JsonObject.long(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.runCatching { asLong }?.getOrNull()
private fun JsonObject.int(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.runCatching { asInt }?.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.let { element ->
    runCatching { element.asBoolean }.getOrElse {
        runCatching { element.asInt != 0 }.getOrNull()
    }
}
