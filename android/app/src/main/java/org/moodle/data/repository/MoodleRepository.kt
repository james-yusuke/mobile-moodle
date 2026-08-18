package org.moodle.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.moodle.core.model.AssignmentSubmissionStatus
import org.moodle.core.model.AuthState
import org.moodle.core.model.AssignmentPayload
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.ConversationType
import org.moodle.core.model.MessageDraft
import org.moodle.core.model.MoodleAssignment
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleConversationMember
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleError
import org.moodle.core.model.MoodleFile
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.MoodleMessageUser
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.MoodleModuleContent
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.MoodleResult
import org.moodle.core.model.MoodleSection
import org.moodle.core.model.SiteAccount
import org.moodle.core.network.MoodleApi
import org.moodle.core.html.HtmlMoodleDataSource
import org.moodle.core.html.HtmlMoodleException
import org.moodle.core.network.MoodleUrl
import org.moodle.core.security.SecureCredentialStore
import org.moodle.data.local.CalendarEventEntity
import org.moodle.data.local.MessageDraftEntity
import org.moodle.data.local.MessageSyncStateEntity
import org.moodle.data.local.MoodleDao
import org.moodle.data.local.NotificationEntity
import org.moodle.data.local.toDomain
import org.moodle.data.local.toEntity
import java.io.File
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MoodleRepository {
    fun courses(accountId: String): Flow<List<MoodleCourse>>
    fun sections(accountId: String, courseId: Long): Flow<List<MoodleSection>>
    fun grades(accountId: String): Flow<List<MoodleGrade>>
    fun events(accountId: String): Flow<List<MoodleCalendarEvent>>
    fun notifications(accountId: String): Flow<List<MoodleNotification>>
    fun conversations(accountId: String): Flow<List<MoodleConversation>>
    fun messages(accountId: String, conversationId: Long): Flow<List<MoodleMessage>>
    fun messageDraft(accountId: String, draftKey: String): Flow<MessageDraft?>
    suspend fun sync(accountId: String): MoodleResult<Unit>
    suspend fun syncMessages(accountId: String, allowNotifications: Boolean = true): MoodleResult<Unit>
    suspend fun refreshConversations(accountId: String, offset: Int = 0): MoodleResult<List<MoodleConversation>>
    suspend fun refreshMessages(
        accountId: String,
        conversationId: Long,
        offset: Int = 0,
    ): MoodleResult<List<MoodleMessage>>
    suspend fun searchMessageUsers(accountId: String, query: String): MoodleResult<List<MoodleMessageUser>>
    suspend fun sendMessage(accountId: String, conversationId: Long, text: String): MoodleResult<Unit>
    suspend fun startConversation(accountId: String, userId: Long, text: String): MoodleResult<Long>
    suspend fun markConversationRead(accountId: String, conversationId: Long): MoodleResult<Unit>
    suspend fun saveMessageDraft(accountId: String, draftKey: String, body: String)
    suspend fun refreshCourse(accountId: String, courseId: Long): MoodleResult<List<MoodleSection>>
    suspend fun assignments(accountId: String, courseId: Long): MoodleResult<List<MoodleAssignment>>
    suspend fun moduleContent(accountId: String, module: MoodleModule): MoodleResult<MoodleModuleContent>
    suspend fun submissionStatus(accountId: String, assignmentId: Long): MoodleResult<AssignmentSubmissionStatus>
    suspend fun submitAssignment(
        accountId: String,
        assignment: MoodleAssignment,
        onlineText: String,
        fileUri: Uri?,
    ): MoodleResult<Unit>
    suspend fun markNotificationRead(accountId: String, notificationId: Long): MoodleResult<Unit>
    suspend fun authenticatedWebUrl(accountId: String, targetUrl: String): String
    suspend fun cacheFile(accountId: String, file: MoodleFile): MoodleResult<File>
}

@Singleton
class DefaultMoodleRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MoodleApi,
    private val client: OkHttpClient,
    private val dao: MoodleDao,
    private val tokens: SecureCredentialStore,
    private val htmlDataSource: HtmlMoodleDataSource,
    private val gson: Gson,
) : MoodleRepository {
    override fun courses(accountId: String) = dao.observeCourses(accountId).map { rows -> rows.map { it.toDomain() } }
    override fun sections(accountId: String, courseId: Long) = dao.observeSections(accountId, courseId).map { rows ->
        rows.map { it.toDomain(gson) }
    }
    override fun grades(accountId: String) = dao.observeGrades(accountId).map { rows -> rows.map { it.toDomain() } }
    override fun events(accountId: String) = dao.observeEvents(accountId).map { rows -> rows.map { it.toDomain() } }
    override fun notifications(accountId: String) = dao.observeNotifications(accountId).map { rows -> rows.map { it.toDomain() } }
    override fun conversations(accountId: String) = dao.observeConversations(accountId).map { rows ->
        rows.map { it.toDomain(gson) }
    }
    override fun messages(accountId: String, conversationId: Long) =
        dao.observeMessages(accountId, conversationId).map { rows -> rows.map { it.toDomain() } }
    override fun messageDraft(accountId: String, draftKey: String) =
        dao.observeMessageDraft(accountId, draftKey).map { it?.toDomain() }

    override suspend fun sync(accountId: String): MoodleResult<Unit> = safely {
        val account = account(accountId)
        try {
            if (account.connectionMode == ConnectionMode.NativeApi) syncApi(account) else syncHtml(account)
            dao.upsertAccount(
                account.copy(authState = AuthState.Authenticated, lastSyncEpochSeconds = nowEpochSeconds()).toEntity(gson),
            )
        } catch (error: Throwable) {
            if (error.asMoodleError().requiresReauthentication) {
                dao.upsertAccount(account.copy(authState = AuthState.ReauthenticationRequired).toEntity(gson))
            }
            throw error
        }
    }

    override suspend fun syncMessages(accountId: String, allowNotifications: Boolean): MoodleResult<Unit> = safely {
        val account = account(accountId)
        if (!account.capabilities.messages.canList) return@safely
        try {
            val initialMessageCache = dao.getMessageSyncInitialized(account.id) != true
            val conversations = fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE)
            storeConversations(account, conversations, replace = true)
            conversations.asSequence()
                .filter { it.unreadCount > 0 }
                .take(MAX_BACKGROUND_CONVERSATIONS)
                .forEach { conversation ->
                    try {
                        val messages = fetchMessages(account, conversation.id, 0, MESSAGE_PAGE_SIZE)
                        storeMessages(
                            account,
                            conversation.id,
                            messages,
                            suppressNotifications = initialMessageCache || !allowNotifications,
                        )
                    } catch (error: Throwable) {
                        if (error.asMoodleError().requiresReauthentication) throw error
                    }
                }
            dao.upsertMessageSyncState(MessageSyncStateEntity(account.id, true, nowEpochSeconds()))
        } catch (error: Throwable) {
            if (error.asMoodleError().requiresReauthentication) {
                dao.upsertAccount(account.copy(authState = AuthState.ReauthenticationRequired).toEntity(gson))
            }
            throw error
        }
    }

    override suspend fun refreshConversations(
        accountId: String,
        offset: Int,
    ): MoodleResult<List<MoodleConversation>> = safely {
        val account = account(accountId)
        require(account.capabilities.messages.canList) { "Messages are not supported by this site" }
        val conversations = fetchConversations(account, offset.coerceAtLeast(0), MESSAGE_CONVERSATION_PAGE_SIZE)
        storeConversations(account, conversations, replace = offset <= 0)
        conversations
    }

    override suspend fun refreshMessages(
        accountId: String,
        conversationId: Long,
        offset: Int,
    ): MoodleResult<List<MoodleMessage>> = safely {
        val account = account(accountId)
        require(account.capabilities.messages.canRead) { "Message history is not supported by this site" }
        val messages = fetchMessages(account, conversationId, offset.coerceAtLeast(0), MESSAGE_PAGE_SIZE)
        storeMessages(account, conversationId, messages, suppressNotifications = true)
        messages
    }

    override suspend fun searchMessageUsers(
        accountId: String,
        query: String,
    ): MoodleResult<List<MoodleMessageUser>> = safely {
        val trimmed = query.trim()
        require(trimmed.length >= 2) { "Enter at least two characters" }
        val account = account(accountId)
        require(account.capabilities.messages.canSearchUsers) { "User search is not supported by this site" }
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            return@safely htmlDataSource.searchMessageUsers(account, trimmed, MESSAGE_SEARCH_LIMIT)
        }
        val response = call(
            account,
            "core_message_message_search_users",
            mapOf(
                "userid" to requiredUserId(account),
                "search" to trimmed,
                "limitfrom" to "0",
                "limitnum" to MESSAGE_SEARCH_LIMIT.toString(),
            ),
        )
        parseMessageUsers(response, account.userId).filter { it.canMessage }.take(MESSAGE_SEARCH_LIMIT)
    }

    override suspend fun sendMessage(
        accountId: String,
        conversationId: Long,
        text: String,
    ): MoodleResult<Unit> = safely {
        val body = validatedMessageBody(text)
        val account = account(accountId)
        require(account.capabilities.messages.canSend) { "Sending messages is not supported by this site" }
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            htmlDataSource.sendConversationMessage(account, conversationId, body)
        } else {
            call(
                account,
                "core_message_send_messages_to_conversation",
                mapOf(
                    "conversationid" to conversationId.toString(),
                    "messages[0][text]" to body,
                    "messages[0][textformat]" to "0",
                ),
            )
        }
        dao.deleteMessageDraft(accountId, conversationDraftKey(conversationId))
        val messages = fetchMessages(account, conversationId, 0, MESSAGE_PAGE_SIZE)
        storeMessages(account, conversationId, messages, suppressNotifications = true)
        runCatching {
            storeConversations(account, fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE), replace = true)
        }
    }

    override suspend fun startConversation(
        accountId: String,
        userId: Long,
        text: String,
    ): MoodleResult<Long> = safely {
        val body = validatedMessageBody(text)
        val account = account(accountId)
        require(account.capabilities.messages.canStartConversation && account.capabilities.messages.canSearchUsers) {
            "Starting a conversation is not supported by this site"
        }
        val conversationId = if (account.connectionMode == ConnectionMode.NativeHtml) {
            htmlDataSource.sendDirectMessage(account, userId, body)
        } else {
            val sent = call(
                account,
                "core_message_send_instant_messages",
                mapOf(
                    "messages[0][touserid]" to userId.toString(),
                    "messages[0][text]" to body,
                    "messages[0][textformat]" to "0",
                    "messages[0][clientmsgid]" to "android-${System.currentTimeMillis()}",
                ),
            )
            val returnedId = sent.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject?.long("conversationid")
            returnedId ?: if (account.capabilities.supports("core_message_get_conversation_between_users")) {
                call(
                    account,
                    "core_message_get_conversation_between_users",
                    mapOf(
                        "userid" to requiredUserId(account),
                        "otheruserid" to userId.toString(),
                        "includecontactrequests" to "0",
                        "includeprivacyinfo" to "0",
                        "memberlimit" to "0",
                        "memberoffset" to "0",
                        "messagelimit" to "1",
                        "messageoffset" to "0",
                        "newestmessagesfirst" to "1",
                    ),
                ).asJsonObject.long("id")
            } else {
                fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE)
                    .firstOrNull { conversation -> conversation.members.any { it.id == userId } }
                    ?.id
            } ?: throw MoodleRepositoryException(
                "message_sent_refresh_needed",
                "The message was sent, but Moodle did not return the conversation. Refresh messages to continue.",
            )
        }
        dao.deleteMessageDraft(accountId, userDraftKey(userId))
        storeConversations(account, fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE), replace = true)
        val messages = fetchMessages(account, conversationId, 0, MESSAGE_PAGE_SIZE)
        storeMessages(account, conversationId, messages, suppressNotifications = true)
        conversationId
    }

    override suspend fun markConversationRead(
        accountId: String,
        conversationId: Long,
    ): MoodleResult<Unit> = safely {
        val account = account(accountId)
        require(account.capabilities.messages.canMarkRead) { "Read state is not supported by this site" }
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            htmlDataSource.markConversationRead(account, conversationId)
        } else {
            call(
                account,
                "core_message_mark_all_conversation_messages_as_read",
                mapOf(
                    "userid" to requiredUserId(account),
                    "conversationid" to conversationId.toString(),
                ),
            )
        }
        dao.markConversationMessagesRead(accountId, conversationId)
        dao.markConversationRead(accountId, conversationId)
    }

    override suspend fun saveMessageDraft(accountId: String, draftKey: String, body: String) {
        if (body.isBlank()) {
            dao.deleteMessageDraft(accountId, draftKey)
        } else {
            dao.upsertMessageDraft(
                MessageDraftEntity(accountId, draftKey, body.take(MAX_MESSAGE_LENGTH), System.currentTimeMillis() / 1_000L),
            )
        }
    }

    override suspend fun refreshCourse(accountId: String, courseId: Long): MoodleResult<List<MoodleSection>> = safely {
        val account = account(accountId)
        require(account.capabilities.contents) { "Course contents are not supported by this site" }
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            val sections = htmlDataSource.sections(account, courseId)
            dao.replaceSections(accountId, courseId, sections.map { it.toEntity(accountId, gson) })
            return@safely sections
        }
        val response = call(account, "core_course_get_contents", mapOf("courseid" to courseId.toString()))
        val sections = response.asJsonArray.mapIndexed { position, item ->
            val section = item.asJsonObject
            MoodleSection(
                id = section.long("id") ?: position.toLong(),
                courseId = courseId,
                name = section.string("name").orEmpty().ifBlank { "Section ${position + 1}" },
                summaryHtml = section.string("summary").orEmpty(),
                position = section.int("section") ?: position,
                modules = section.array("modules").map { moduleElement -> parseModule(moduleElement.asJsonObject) },
            )
        }
        dao.replaceSections(accountId, courseId, sections.map { it.toEntity(accountId, gson) })
        sections
    }

    override suspend fun assignments(accountId: String, courseId: Long): MoodleResult<List<MoodleAssignment>> = safely {
        val account = account(accountId)
        require(account.capabilities.assignments) { "Assignments are not supported by this site" }
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            val sections = htmlDataSource.sections(account, courseId)
            return@safely sections.flatMap { section ->
                section.modules.filter { it.moduleType == "assign" }.map { module ->
                    MoodleAssignment(
                        module.instanceId ?: module.id,
                        courseId,
                        module.id,
                        module.name,
                        module.descriptionHtml,
                        null,
                        null,
                        false,
                        false,
                        false,
                    )
                }
            }
        }
        val response = call(account, "mod_assign_get_assignments", mapOf("courseids[0]" to courseId.toString()))
        response.asJsonObject.array("courses").flatMap { courseElement ->
            courseElement.asJsonObject.array("assignments").map { parseAssignment(courseId, it.asJsonObject) }
        }
    }

    override suspend fun moduleContent(
        accountId: String,
        module: MoodleModule,
    ): MoodleResult<MoodleModuleContent> = safely {
        val account = account(accountId)
        val originalUrl = module.webUrl
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            require(!originalUrl.isNullOrBlank()) { "This content has no Moodle URL" }
            return@safely htmlDataSource.moduleContent(account, module.name, originalUrl)
        }
        MoodleModuleContent(module.name, module.descriptionHtml, module.files, originalUrl)
    }

    override suspend fun submissionStatus(
        accountId: String,
        assignmentId: Long,
    ): MoodleResult<AssignmentSubmissionStatus> = safely {
        val account = account(accountId)
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            return@safely AssignmentSubmissionStatus("read_only", false, null, null, null)
        }
        val root = call(
            account,
            "mod_assign_get_submission_status",
            mapOf("assignid" to assignmentId.toString()),
        ).asJsonObject
        val submission = root.obj("lastattempt")?.obj("submission")
        val feedback = root.obj("feedback")
        AssignmentSubmissionStatus(
            status = submission?.string("status") ?: "new",
            graded = feedback != null,
            submittedAt = submission?.long("timemodified"),
            grade = feedback?.obj("grade")?.string("grade"),
            feedbackHtml = feedback?.array("plugins")
                ?.flatMap { it.asJsonObject.array("editorfields") }
                ?.firstOrNull()
                ?.asJsonObject
                ?.string("text"),
        )
    }

    override suspend fun submitAssignment(
        accountId: String,
        assignment: MoodleAssignment,
        onlineText: String,
        fileUri: Uri?,
    ): MoodleResult<Unit> = safely {
        val account = nativeAccount(accountId)
        require(account.capabilities.assignmentSubmission) { "Assignment submission is not supported" }
        require(fileUri == null || assignment.allowsFiles) { "This assignment does not accept files" }
        require(onlineText.isBlank() || assignment.allowsOnlineText) { "This assignment does not accept online text" }

        val draftItemId = fileUri?.let { uploadDraftFile(account, it) }
        val fields = AssignmentPayload.build(assignment, onlineText, draftItemId)
        call(account, "mod_assign_save_submission", fields)
        if (assignment.requiresSubmitButton && account.capabilities.supports("mod_assign_submit_for_grading")) {
            call(
                account,
                "mod_assign_submit_for_grading",
                mapOf("assignmentid" to assignment.id.toString(), "acceptsubmissionstatement" to "1"),
            )
        }
    }

    override suspend fun markNotificationRead(accountId: String, notificationId: Long): MoodleResult<Unit> = safely {
        val account = account(accountId)
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            dao.markNotificationRead(accountId, notificationId)
            return@safely
        }
        call(
            account,
            "core_message_mark_notification_read",
            mapOf(
                "notificationid" to notificationId.toString(),
                "timeread" to nowEpochSeconds().toString(),
            ),
        )
        dao.markNotificationRead(accountId, notificationId)
    }

    override suspend fun authenticatedWebUrl(accountId: String, targetUrl: String): String {
        val account = runCatching { account(accountId) }.getOrNull() ?: return targetUrl
        if (account.connectionMode == ConnectionMode.NativeHtml) return targetUrl
        if (!MoodleUrl.sameSite(account.baseUrl, targetUrl) || !account.capabilities.autoLogin) return targetUrl
        val privateToken = tokens.privateToken(accountId) ?: return targetUrl
        val userId = account.userId ?: return targetUrl
        return runCatching {
            val result = call(account, "tool_mobile_get_autologin_key", mapOf("privatetoken" to privateToken)).asJsonObject
            val autoLoginUrl = result.string("autologinurl") ?: return@runCatching targetUrl
            val key = result.string("key") ?: return@runCatching targetUrl
            if (!MoodleUrl.sameSite(account.baseUrl, autoLoginUrl)) return@runCatching targetUrl
            Uri.parse(autoLoginUrl).buildUpon()
                .appendQueryParameter("userid", userId.toString())
                .appendQueryParameter("key", key)
                .appendQueryParameter("urltogo", targetUrl)
                .build()
                .toString()
        }.getOrDefault(targetUrl)
    }

    override suspend fun cacheFile(accountId: String, file: MoodleFile): MoodleResult<File> = safely {
        val account = account(accountId)
        require(MoodleUrl.sameSite(account.baseUrl, file.url)) { "Refusing a cross-site file URL" }
        val accountDirectory = File(context.filesDir, "moodle-cache/${account.id}").apply { mkdirs() }
        val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "download" }
        val destination = File(accountDirectory, safeName)
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            htmlDataSource.download(account, file.url, destination)
            return@safely destination
        }
        val token = requireToken(accountId)
        val authenticatedUrl = Uri.parse(file.url).buildUpon().appendQueryParameter("token", token).build().toString()
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(authenticatedUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw MoodleRepositoryException("download_failed", "Download failed (${response.code})")
                response.body.byteStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
            }
        }
        destination
    }

    private suspend fun fetchConversations(
        account: SiteAccount,
        offset: Int,
        limit: Int,
    ): List<MoodleConversation> {
        if (account.connectionMode == ConnectionMode.NativeHtml) {
            return htmlDataSource.conversations(account, offset, limit)
        }
        val response = call(
            account,
            "core_message_get_conversations",
            mapOf(
                "userid" to requiredUserId(account),
                "limitfrom" to offset.toString(),
                "limitnum" to limit.toString(),
            ),
        )
        return response.asJsonObject.array("conversations").mapNotNull { element ->
            parseConversation(element.asJsonObject, account.userId ?: 0)
        }
    }

    private suspend fun storeConversations(
        account: SiteAccount,
        conversations: List<MoodleConversation>,
        replace: Boolean,
    ) {
        if (replace) {
            dao.replaceConversations(account.id, conversations.map { it.toEntity(account.id, gson) })
        } else {
            conversations.forEach { dao.upsertConversation(it.toEntity(account.id, gson)) }
        }
        conversations.forEach { conversation ->
            dao.replaceConversationMembers(
                account.id,
                conversation.id,
                conversation.members.map { it.toEntity(account.id, conversation.id) },
            )
        }
    }

    private suspend fun fetchMessages(
        account: SiteAccount,
        conversationId: Long,
        offset: Int,
        limit: Int,
    ): List<MoodleMessage> {
        val members = dao.getConversation(account.id, conversationId)
            ?.toDomain(gson)
            ?.members
            .orEmpty()
            .associate { it.id to it.fullName }
        val rows = if (account.connectionMode == ConnectionMode.NativeHtml) {
            htmlDataSource.conversationMessages(account, conversationId, offset, limit)
        } else {
            val response = call(
                account,
                "core_message_get_conversation_messages",
                mapOf(
                    "currentuserid" to requiredUserId(account),
                    "convid" to conversationId.toString(),
                    "limitfrom" to offset.toString(),
                    "limitnum" to limit.toString(),
                    "newest" to "1",
                    "timefrom" to "0",
                ),
            )
            response.asJsonObject.array("messages").mapNotNull { element ->
                parseMessage(element.asJsonObject, conversationId, account.userId ?: 0, members)
            }
        }
        return rows.map { message ->
            if (message.senderName == "Moodle user" && members[message.senderId] != null) {
                message.copy(senderName = members.getValue(message.senderId))
            } else {
                message
            }
        }.sortedWith(compareBy<MoodleMessage> { it.createdAt }.thenBy { it.id })
    }

    private suspend fun storeMessages(
        account: SiteAccount,
        conversationId: Long,
        messages: List<MoodleMessage>,
        suppressNotifications: Boolean = false,
    ) {
        val known = dao.getKnownMessageIds(account.id).toSet()
        dao.upsertMessages(
            messages.map { message ->
                message.toEntity(
                    account.id,
                    announced = suppressNotifications || message.isMine || message.id in known,
                )
            },
        )
        dao.trimMessages(account.id, conversationId, MAX_CACHED_MESSAGES)
    }

    private fun parseConversation(value: JsonObject, currentUserId: Long): MoodleConversation? {
        val id = value.long("id") ?: return null
        val members = value.array("members").mapNotNull { memberElement ->
            val member = memberElement.asJsonObject
            val memberId = member.long("id") ?: return@mapNotNull null
            MoodleConversationMember(
                memberId,
                member.string("fullname") ?: member.string("name") ?: "Moodle user",
                memberId == currentUserId,
                member.boolean("canmessage") ?: true,
            )
        }
        val type = when (value.int("type")) {
            1 -> ConversationType.Individual
            2 -> ConversationType.Group
            3 -> ConversationType.Self
            else -> ConversationType.Unknown
        }
        val last = value.array("messages").firstOrNull()?.asJsonObject
        val previewHtml = last?.string("text") ?: last?.string("smallmessage") ?: value.string("smallmessage").orEmpty()
        val name = value.string("name").orEmpty().ifBlank {
            members.filterNot { it.isCurrentUser }.joinToString(", ") { it.fullName }
        }.ifBlank {
            when (type) {
                ConversationType.Group -> "Group conversation"
                ConversationType.Self -> "Personal space"
                else -> "Conversation"
            }
        }
        return MoodleConversation(
            id,
            type,
            name,
            members,
            safeText(previewHtml),
            value.long("timemodified") ?: last?.long("timecreated") ?: 0,
            value.int("unreadcount") ?: 0,
            value.boolean("isfavourite") ?: false,
            type == ConversationType.Group || members.any { !it.isCurrentUser && it.canMessage },
        )
    }

    private fun parseMessage(
        value: JsonObject,
        conversationId: Long,
        currentUserId: Long,
        memberNames: Map<Long, String>,
    ): MoodleMessage? {
        val id = value.long("id") ?: value.long("msgid") ?: return null
        val senderId = value.long("useridfrom") ?: value.long("userid") ?: 0
        val rawHtml = value.string("text") ?: value.string("fullmessagehtml") ?: value.string("fullmessage").orEmpty()
        val safeHtml = safeMessageHtml(rawHtml)
        return MoodleMessage(
            id,
            value.long("conversationid") ?: conversationId,
            senderId,
            value.string("userfullname") ?: value.string("sendername") ?: memberNames[senderId] ?: "Moodle user",
            Jsoup.parse(safeHtml).text(),
            safeHtml,
            value.long("timecreated") ?: nowEpochSeconds(),
            senderId == currentUserId,
            senderId == currentUserId || (value.long("timeread") ?: 0) > 0 || value.boolean("isread") == true,
        )
    }

    private fun parseMessageUsers(response: JsonElement, currentUserId: Long?): List<MoodleMessageUser> {
        val result = mutableListOf<MoodleMessageUser>()
        fun visit(element: JsonElement) {
            when {
                element.isJsonArray -> element.asJsonArray.forEach(::visit)
                element.isJsonObject -> {
                    val value = element.asJsonObject
                    val id = value.long("id") ?: value.long("userid")
                    val name = value.string("fullname") ?: value.string("name")
                    if (id != null && id != currentUserId && !name.isNullOrBlank()) {
                        result += MoodleMessageUser(id, name, value.boolean("canmessage") ?: true)
                    } else {
                        value.entrySet().forEach { visit(it.value) }
                    }
                }
            }
        }
        visit(response)
        return result.distinctBy { it.id }
    }

    private fun validatedMessageBody(text: String): String {
        val value = text.trim()
        require(value.isNotBlank()) { "Message cannot be empty" }
        require(value.length <= MAX_MESSAGE_LENGTH) { "Message is too long" }
        return value
    }

    private fun safeMessageHtml(html: String): String = org.jsoup.Jsoup.clean(
        html,
        "",
        org.jsoup.safety.Safelist.none().addTags("br", "p", "a").addAttributes("a", "href", "title")
            .addProtocols("a", "href", "https"),
        org.jsoup.nodes.Document.OutputSettings().prettyPrint(false),
    )

    private fun safeText(html: String): String = Jsoup.parse(safeMessageHtml(html)).text()

    private suspend fun syncApi(account: SiteAccount) {
        if (account.capabilities.courses) syncCourses(account)
        if (account.capabilities.grades && account.userId != null) runCatching { syncGrades(account) }
        if (account.capabilities.calendar) runCatching { syncEvents(account) }
        if (account.capabilities.notifications && account.userId != null) runCatching { syncNotifications(account) }
        if (account.capabilities.messages.canList && account.userId != null) runCatching {
            val conversations = fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE)
            storeConversations(account, conversations, replace = true)
        }
    }

    private suspend fun syncHtml(account: SiteAccount) {
        val courses = htmlDataSource.courses(account)
        dao.replaceCourses(account.id, courses.map { it.toEntity(account.id) })
        if (account.capabilities.grades) {
            runCatching { htmlDataSource.grades(account, courses) }
                .onSuccess { grades -> dao.replaceGrades(account.id, grades.map { it.toEntity(account.id) }) }
        }
        if (account.capabilities.calendar) {
            runCatching { htmlDataSource.events(account) }
                .onSuccess { events -> dao.replaceEvents(account.id, events.map { it.toEntity(account.id) }) }
        }
        if (account.capabilities.notifications) {
            val known = dao.getKnownNotificationIds(account.id).toSet()
            runCatching { htmlDataSource.notifications(account) }
                .onSuccess { notifications ->
                    dao.upsertNotifications(notifications.map { it.toEntity(account.id, it.id in known) })
                }
        }
        if (account.capabilities.messages.canList) {
            runCatching {
                val conversations = fetchConversations(account, 0, MESSAGE_CONVERSATION_PAGE_SIZE)
                storeConversations(account, conversations, replace = true)
            }
        }
    }

    private suspend fun syncCourses(account: SiteAccount) {
        val response = call(account, "core_enrol_get_users_courses", mapOf("userid" to requiredUserId(account)))
        val courses = response.asJsonArray.map { item ->
            val value = item.asJsonObject
            MoodleCourse(
                id = value.long("id") ?: 0,
                shortName = value.string("shortname").orEmpty(),
                fullName = value.string("fullname").orEmpty(),
                summaryHtml = value.string("summary").orEmpty(),
                startDate = value.long("startdate"),
                endDate = value.long("enddate")?.takeIf { it > 0 },
            )
        }.filter { it.id > 0 }
        dao.replaceCourses(account.id, courses.map { it.toEntity(account.id) })
    }

    private suspend fun syncGrades(account: SiteAccount) {
        val rows = mutableListOf<org.moodle.data.local.GradeEntity>()
        val courseRows = dao.getCourses(account.id)
        for (course in courseRows) {
            val response = call(
                account,
                "gradereport_user_get_grade_items",
                mapOf("courseid" to course.courseId.toString(), "userid" to requiredUserId(account)),
            )
            response.asJsonObject.array("usergrades").forEach { userGrade ->
                userGrade.asJsonObject.array("gradeitems").forEach { gradeElement ->
                    val grade = gradeElement.asJsonObject
                    val itemId = grade.long("id") ?: return@forEach
                    rows += MoodleGrade(
                        courseId = course.courseId,
                        itemId = itemId,
                        itemName = grade.string("itemname") ?: grade.string("itemtype").orEmpty(),
                        gradeFormatted = grade.string("gradeformatted").orEmpty(),
                        rangeFormatted = grade.string("rangeformatted").orEmpty(),
                        percentageFormatted = grade.string("percentageformatted").orEmpty(),
                    ).toEntity(account.id)
                }
            }
        }
        dao.replaceGrades(account.id, rows)
    }

    private suspend fun syncEvents(account: SiteAccount) {
        val response = call(account, "core_calendar_get_calendar_upcoming_view", emptyMap())
        val events = response.asJsonObject.array("events").mapNotNull { eventElement ->
            val event = eventElement.asJsonObject
            val id = event.long("id") ?: return@mapNotNull null
            MoodleCalendarEvent(
                id,
                event.string("name").orEmpty(),
                event.string("description").orEmpty(),
                event.long("timestart") ?: 0,
                event.long("courseid")?.takeIf { it > 0 },
                event.string("url"),
            ).toEntity(account.id)
        }
        dao.replaceEvents(account.id, events)
    }

    private suspend fun syncNotifications(account: SiteAccount) {
        val known = dao.getKnownNotificationIds(account.id).toSet()
        val response = call(
            account,
            "core_message_get_messages",
            mapOf(
                "useridto" to requiredUserId(account),
                "useridfrom" to "0",
                "type" to "notifications",
                "read" to "both",
                "newestfirst" to "1",
                "limitnum" to "50",
            ),
        )
        val rows = response.asJsonObject.array("messages").mapNotNull { item ->
            val value = item.asJsonObject
            val id = value.long("id") ?: return@mapNotNull null
            NotificationEntity(
                accountId = account.id,
                notificationId = id,
                subject = value.string("subject") ?: value.string("smallmessage").orEmpty(),
                fullMessageHtml = value.string("fullmessagehtml") ?: value.string("fullmessage").orEmpty(),
                createdAt = value.long("timecreated") ?: 0,
                read = (value.long("timeread") ?: 0) > 0,
                contextUrl = value.string("contexturl"),
                locallyNotified = id in known,
            )
        }
        dao.upsertNotifications(rows)
    }

    private suspend fun uploadDraftFile(account: SiteAccount, uri: Uri): Long {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(120) ?: "submission"
        val temporary = withContext(Dispatchers.IO) {
            File.createTempFile("moodle-upload-", ".tmp", context.cacheDir).also { file ->
                resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: throw MoodleRepositoryException("file_unavailable", "The selected file could not be read")
            }
        }
        return try {
            val response = api.uploadFile(
                "${account.baseUrl}/webservice/upload.php",
                requireToken(account.id).toRequestBody("text/plain".toMediaTypeOrNull()),
                "draft".toRequestBody("text/plain".toMediaTypeOrNull()),
                "0".toRequestBody("text/plain".toMediaTypeOrNull()),
                MultipartBody.Part.createFormData("file_1", name, temporary.asRequestBody(mimeType.toMediaTypeOrNull())),
            )
            response.asJsonArray.firstOrNull()?.asJsonObject?.long("itemid")
                ?: throw MoodleRepositoryException("upload_failed", "Moodle did not return a draft item id")
        } finally {
            temporary.delete()
        }
    }

    private suspend fun nativeAccount(accountId: String): SiteAccount {
        val account = account(accountId)
        require(account.connectionMode == ConnectionMode.NativeApi) { "This operation requires the Moodle API" }
        return account
    }

    private suspend fun account(accountId: String): SiteAccount = dao.getAccount(accountId)?.toDomain(gson)
        ?: throw MoodleRepositoryException("account_missing", "The Moodle account no longer exists")

    private fun requireToken(accountId: String): String = tokens.token(accountId)
        ?: throw MoodleRepositoryException("invalidtoken", "Please sign in again")

    private fun requiredUserId(account: SiteAccount): String = account.userId?.toString()
        ?: throw MoodleRepositoryException("account_invalid", "The Moodle account has no user id")

    private suspend fun call(account: SiteAccount, function: String, parameters: Map<String, String>): JsonElement {
        if (account.capabilities.functions.isNotEmpty() && function !in account.capabilities.functions) {
            throw MoodleRepositoryException("unsupported_function", "$function is not available on this site")
        }
        val result = api.restCall(
            "${account.baseUrl}/webservice/rest/server.php",
            mapOf(
                "wstoken" to requireToken(account.id),
                "wsfunction" to function,
                "moodlewsrestformat" to "json",
            ) + parameters,
        )
        if (result.isJsonObject && result.asJsonObject.has("exception")) {
            val error = result.asJsonObject
            throw MoodleRepositoryException(
                error.string("errorcode") ?: "webservice_error",
                error.string("message") ?: "Moodle web service error",
            )
        }
        return result
    }

    private fun parseModule(value: JsonObject): MoodleModule = MoodleModule(
        id = value.long("id") ?: 0,
        instanceId = value.long("instance"),
        name = value.string("name").orEmpty(),
        moduleType = value.string("modname").orEmpty(),
        descriptionHtml = value.string("description").orEmpty(),
        webUrl = value.string("url"),
        files = value.array("contents").mapNotNull { fileElement ->
            val file = fileElement.asJsonObject
            val url = file.string("fileurl") ?: return@mapNotNull null
            MoodleFile(
                name = file.string("filename") ?: "file",
                url = url,
                mimeType = file.string("mimetype"),
                sizeBytes = file.long("filesize"),
            )
        },
    )

    private fun parseAssignment(courseId: Long, value: JsonObject): MoodleAssignment {
        val configs = value.array("configs").associate { configElement ->
            val config = configElement.asJsonObject
            config.string("plugin").orEmpty() + ":" + config.string("name").orEmpty() to config.string("value").orEmpty()
        }
        return MoodleAssignment(
            id = value.long("id") ?: 0,
            courseId = courseId,
            courseModuleId = value.long("cmid") ?: 0,
            name = value.string("name").orEmpty(),
            introHtml = value.string("intro").orEmpty(),
            dueDate = value.long("duedate")?.takeIf { it > 0 },
            cutoffDate = value.long("cutoffdate")?.takeIf { it > 0 },
            allowsOnlineText = configs.keys.any { it.startsWith("onlinetext:") },
            allowsFiles = configs.keys.any { it.startsWith("file:") },
            requiresSubmitButton = value.bool("submissiondrafts"),
        )
    }
}

private fun JsonObject.array(name: String) = getAsJsonArray(name)?.toList().orEmpty()
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.int(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.runCatching { asInt }?.getOrNull()
private fun JsonObject.bool(name: String): Boolean = get(name)?.takeUnless { it.isJsonNull }?.runCatching {
    if (isJsonPrimitive && asJsonPrimitive.isBoolean) asBoolean else asInt == 1
}?.getOrDefault(false) ?: false
private fun JsonObject.boolean(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.runCatching {
    if (isJsonPrimitive && asJsonPrimitive.isBoolean) asBoolean else asInt != 0
}?.getOrNull()
private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

fun conversationDraftKey(conversationId: Long): String = "conversation:$conversationId"
fun userDraftKey(userId: Long): String = "user:$userId"

private const val MESSAGE_CONVERSATION_PAGE_SIZE = 30
private const val MESSAGE_PAGE_SIZE = 50
private const val MESSAGE_SEARCH_LIMIT = 30
private const val MAX_CACHED_MESSAGES = 200
private const val MAX_BACKGROUND_CONVERSATIONS = 10
private const val MAX_MESSAGE_LENGTH = 4_000
