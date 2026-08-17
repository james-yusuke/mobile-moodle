package org.moodle.core.model

enum class ConnectionMode {
    NativeApi,
    NativeHtml,
}

enum class AuthState { Authenticated, ReauthenticationRequired }

enum class HtmlThemeFamily { Modern, Legacy, StructuralFallback }

enum class HtmlFeature {
    Courses,
    Contents,
    AssignmentsRead,
    Grades,
    Calendar,
    Notifications,
    Files,
    MessagesRead,
    MessagesSearch,
    MessagesSend,
    MessagesMarkRead,
}

data class SiteAccount(
    val id: String,
    val baseUrl: String,
    val siteName: String,
    val username: String?,
    val userId: Long?,
    val fullName: String?,
    val connectionMode: ConnectionMode,
    val capabilities: SiteCapabilities = SiteCapabilities(),
    val authState: AuthState = AuthState.Authenticated,
    val moodleVersion: String? = null,
    val themeFamily: HtmlThemeFamily? = null,
    val lastSyncEpochSeconds: Long? = null,
    val isActive: Boolean = false,
)

data class SiteCapabilities(
    val functions: Set<String> = emptySet(),
    val htmlFeatures: Set<HtmlFeature> = emptySet(),
) {
    val courses: Boolean get() = supports("core_enrol_get_users_courses") || HtmlFeature.Courses in htmlFeatures
    val contents: Boolean get() = supports("core_course_get_contents") || HtmlFeature.Contents in htmlFeatures
    val assignments: Boolean get() = supports("mod_assign_get_assignments") || HtmlFeature.AssignmentsRead in htmlFeatures
    val assignmentSubmission: Boolean
        get() = supports("mod_assign_get_assignments") && supports("mod_assign_save_submission")
    val grades: Boolean
        get() = supports("gradereport_user_get_grade_items") || HtmlFeature.Grades in htmlFeatures
    val calendar: Boolean
        get() = supports("core_calendar_get_calendar_upcoming_view") || HtmlFeature.Calendar in htmlFeatures
    val notifications: Boolean
        get() = supports("core_message_get_messages") || HtmlFeature.Notifications in htmlFeatures
    val messages: MessageCapabilities
        get() = MessageCapabilities(
            canList = supports("core_message_get_conversations") || HtmlFeature.MessagesRead in htmlFeatures,
            canRead = supports("core_message_get_conversation_messages") || HtmlFeature.MessagesRead in htmlFeatures,
            canSearchUsers = supports("core_message_message_search_users") || HtmlFeature.MessagesSearch in htmlFeatures,
            canSend = (
                supports("core_message_send_messages_to_conversation") ||
                    HtmlFeature.MessagesSend in htmlFeatures
                ),
            canStartConversation = supports("core_message_send_instant_messages") ||
                HtmlFeature.MessagesSend in htmlFeatures,
            canMarkRead = supports("core_message_mark_all_conversation_messages_as_read") ||
                HtmlFeature.MessagesMarkRead in htmlFeatures,
        )
    val autoLogin: Boolean
        get() = supports("tool_mobile_get_autologin_key")

    fun supports(function: String): Boolean = function in functions
}

data class MessageCapabilities(
    val canList: Boolean = false,
    val canRead: Boolean = false,
    val canSearchUsers: Boolean = false,
    val canSend: Boolean = false,
    val canStartConversation: Boolean = false,
    val canMarkRead: Boolean = false,
)

data class MoodlePublicConfig(
    val canonicalUrl: String,
    val siteName: String,
    val mobileWebServiceEnabled: Boolean,
    val loginType: Int,
    val launchUrl: String?,
    val showLoginForm: Boolean,
) {
    val connectionMode: ConnectionMode
        get() = if (mobileWebServiceEnabled) ConnectionMode.NativeApi else ConnectionMode.NativeHtml

    val browserSsoRequired: Boolean get() = loginType == 2 || loginType == 3
}

data class MoodleCourse(
    val id: Long,
    val shortName: String,
    val fullName: String,
    val summaryHtml: String,
    val startDate: Long?,
    val endDate: Long?,
)

data class MoodleSection(
    val id: Long,
    val courseId: Long,
    val name: String,
    val summaryHtml: String,
    val position: Int,
    val modules: List<MoodleModule>,
)

data class MoodleModule(
    val id: Long,
    val instanceId: Long?,
    val name: String,
    val moduleType: String,
    val descriptionHtml: String,
    val webUrl: String?,
    val files: List<MoodleFile>,
) {
    val nativeType: NativeModuleType
        get() = when (moduleType) {
            "assign" -> NativeModuleType.Assignment
            "page" -> NativeModuleType.Page
            "url" -> NativeModuleType.Url
            "resource" -> NativeModuleType.Resource
            "folder" -> NativeModuleType.Folder
            else -> NativeModuleType.Unsupported
        }
}

enum class NativeModuleType { Assignment, Page, Url, Resource, Folder, Unsupported }

data class MoodleModuleContent(
    val title: String,
    val bodyHtml: String,
    val files: List<MoodleFile>,
    val originalUrl: String?,
)

data class MoodleFile(
    val name: String,
    val url: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

data class MoodleAssignment(
    val id: Long,
    val courseId: Long,
    val courseModuleId: Long,
    val name: String,
    val introHtml: String,
    val dueDate: Long?,
    val cutoffDate: Long?,
    val allowsOnlineText: Boolean,
    val allowsFiles: Boolean,
    val requiresSubmitButton: Boolean,
)

data class AssignmentSubmissionStatus(
    val status: String,
    val graded: Boolean,
    val submittedAt: Long?,
    val grade: String?,
    val feedbackHtml: String?,
)

data class MoodleGrade(
    val courseId: Long,
    val itemId: Long,
    val itemName: String,
    val gradeFormatted: String,
    val rangeFormatted: String,
    val percentageFormatted: String,
)

data class MoodleCalendarEvent(
    val id: Long,
    val name: String,
    val descriptionHtml: String,
    val startEpochSeconds: Long,
    val courseId: Long?,
    val actionUrl: String?,
)

data class MoodleNotification(
    val id: Long,
    val subject: String,
    val fullMessageHtml: String,
    val createdAt: Long,
    val read: Boolean,
    val contextUrl: String?,
)

enum class ConversationType { Individual, Group, Self, Unknown }

data class MoodleConversationMember(
    val id: Long,
    val fullName: String,
    val isCurrentUser: Boolean,
    val canMessage: Boolean,
)

data class MoodleConversation(
    val id: Long,
    val type: ConversationType,
    val name: String,
    val members: List<MoodleConversationMember>,
    val latestMessagePreview: String,
    val latestMessageAt: Long,
    val unreadCount: Int,
    val isFavourite: Boolean,
    val canReply: Boolean,
)

data class MoodleMessage(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String,
    val bodyText: String,
    val bodyHtml: String,
    val createdAt: Long,
    val isMine: Boolean,
    val isRead: Boolean,
)

data class MoodleMessageUser(
    val id: Long,
    val fullName: String,
    val canMessage: Boolean,
)

data class MessageDraft(
    val accountId: String,
    val key: String,
    val body: String,
    val updatedAt: Long,
)

sealed interface MessageSendState {
    data object Idle : MessageSendState
    data object Sending : MessageSendState
    data class Failed(val message: String) : MessageSendState
    data class Sent(val conversationId: Long) : MessageSendState
}

sealed interface MoodleResult<out T> {
    data class Success<T>(val value: T) : MoodleResult<T>
    data class Failure(val error: MoodleError) : MoodleResult<Nothing>
}

data class MoodleError(
    val code: String,
    val message: String,
    val recoverable: Boolean = true,
)
