package org.moodle.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.AuthState
import org.moodle.core.model.ConversationType
import org.moodle.core.model.HtmlFeature
import org.moodle.core.model.HtmlThemeFamily
import org.moodle.core.model.MessageDraft
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleConversationMember
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.MoodleSection
import org.moodle.core.model.SiteAccount
import org.moodle.core.model.SiteCapabilities

@Entity(tableName = "site_accounts", indices = [Index(value = ["baseUrl", "username"])])
data class SiteAccountEntity(
    @PrimaryKey val id: String,
    val baseUrl: String,
    val siteName: String,
    val username: String?,
    val userId: Long?,
    val fullName: String?,
    val connectionMode: String,
    val functionsJson: String,
    val htmlFeaturesJson: String,
    val authState: String,
    val moodleVersion: String?,
    val themeFamily: String?,
    val lastSyncEpochSeconds: Long?,
    val isActive: Boolean,
)

@Entity(tableName = "courses", primaryKeys = ["accountId", "courseId"])
data class CourseEntity(
    val accountId: String,
    val courseId: Long,
    val shortName: String,
    val fullName: String,
    val summaryHtml: String,
    val startDate: Long?,
    val endDate: Long?,
)

@Entity(tableName = "sections", primaryKeys = ["accountId", "courseId", "sectionId"])
data class SectionEntity(
    val accountId: String,
    val courseId: Long,
    val sectionId: Long,
    val name: String,
    val summaryHtml: String,
    val position: Int,
    val modulesJson: String,
)

@Entity(tableName = "grades", primaryKeys = ["accountId", "courseId", "itemId"])
data class GradeEntity(
    val accountId: String,
    val courseId: Long,
    val itemId: Long,
    val itemName: String,
    val gradeFormatted: String,
    val rangeFormatted: String,
    val percentageFormatted: String,
)

@Entity(tableName = "calendar_events", primaryKeys = ["accountId", "eventId"])
data class CalendarEventEntity(
    val accountId: String,
    val eventId: Long,
    val name: String,
    val descriptionHtml: String,
    val startEpochSeconds: Long,
    val courseId: Long?,
    val actionUrl: String?,
)

@Entity(tableName = "notifications", primaryKeys = ["accountId", "notificationId"])
data class NotificationEntity(
    val accountId: String,
    val notificationId: Long,
    val subject: String,
    val fullMessageHtml: String,
    val createdAt: Long,
    val read: Boolean,
    val contextUrl: String?,
    val locallyNotified: Boolean,
)

@Entity(
    tableName = "conversations",
    primaryKeys = ["accountId", "conversationId"],
    indices = [Index(value = ["accountId", "latestMessageAt"])],
)
data class ConversationEntity(
    val accountId: String,
    val conversationId: Long,
    val type: String,
    val name: String,
    val membersJson: String,
    val latestMessagePreview: String,
    val latestMessageAt: Long,
    val unreadCount: Int,
    val isFavourite: Boolean,
    val canReply: Boolean,
)

@Entity(
    tableName = "conversation_members",
    primaryKeys = ["accountId", "conversationId", "userId"],
    indices = [Index(value = ["accountId", "userId"])],
)
data class ConversationMemberEntity(
    val accountId: String,
    val conversationId: Long,
    val userId: Long,
    val fullName: String,
    val isCurrentUser: Boolean,
    val canMessage: Boolean,
)

@Entity(
    tableName = "messages",
    primaryKeys = ["accountId", "messageId"],
    indices = [Index(value = ["accountId", "conversationId", "createdAt"])],
)
data class MessageEntity(
    val accountId: String,
    val messageId: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String,
    val bodyText: String,
    val bodyHtml: String,
    val createdAt: Long,
    val isMine: Boolean,
    val isRead: Boolean,
    val locallyNotified: Boolean,
)

@Entity(tableName = "message_drafts", primaryKeys = ["accountId", "draftKey"])
data class MessageDraftEntity(
    val accountId: String,
    val draftKey: String,
    val body: String,
    val updatedAt: Long,
)

@Entity(tableName = "message_sync_state")
data class MessageSyncStateEntity(
    @PrimaryKey val accountId: String,
    val initialized: Boolean,
    val lastSyncEpochSeconds: Long,
)

@Dao
abstract class MoodleDao {
    @Query("SELECT * FROM site_accounts ORDER BY isActive DESC, siteName COLLATE NOCASE")
    abstract fun observeAccounts(): Flow<List<SiteAccountEntity>>

    @Query("SELECT * FROM site_accounts WHERE isActive = 1 LIMIT 1")
    abstract fun observeActiveAccount(): Flow<SiteAccountEntity?>

    @Query("SELECT * FROM site_accounts WHERE id = :id")
    abstract suspend fun getAccount(id: String): SiteAccountEntity?

    @Query("SELECT * FROM site_accounts WHERE connectionMode IN ('NativeApi', 'NativeHtml') AND authState = 'Authenticated'")
    abstract suspend fun getSyncableAccounts(): List<SiteAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAccount(account: SiteAccountEntity)

    @Query("UPDATE site_accounts SET isActive = 0")
    protected abstract suspend fun clearActiveAccount()

    @Query("UPDATE site_accounts SET isActive = 1 WHERE id = :id")
    protected abstract suspend fun activateAccount(id: String)

    @Transaction
    open suspend fun setActiveAccount(id: String) {
        clearActiveAccount()
        activateAccount(id)
    }

    @Query("DELETE FROM site_accounts WHERE id = :id")
    protected abstract suspend fun deleteAccountRow(id: String)

    @Query("DELETE FROM courses WHERE accountId = :id")
    protected abstract suspend fun deleteCoursesForAccount(id: String)

    @Query("DELETE FROM sections WHERE accountId = :id")
    protected abstract suspend fun deleteSectionsForAccount(id: String)

    @Query("DELETE FROM grades WHERE accountId = :id")
    protected abstract suspend fun deleteGradesForAccount(id: String)

    @Query("DELETE FROM calendar_events WHERE accountId = :id")
    protected abstract suspend fun deleteEventsForAccount(id: String)

    @Query("DELETE FROM notifications WHERE accountId = :id")
    protected abstract suspend fun deleteNotificationsForAccount(id: String)

    @Query("DELETE FROM conversations WHERE accountId = :id")
    protected abstract suspend fun deleteConversationsForAccount(id: String)

    @Query("DELETE FROM conversation_members WHERE accountId = :id")
    protected abstract suspend fun deleteConversationMembersForAccount(id: String)

    @Query("DELETE FROM messages WHERE accountId = :id")
    protected abstract suspend fun deleteMessagesForAccount(id: String)

    @Query("DELETE FROM message_drafts WHERE accountId = :id")
    protected abstract suspend fun deleteMessageDraftsForAccount(id: String)

    @Query("DELETE FROM message_sync_state WHERE accountId = :id")
    protected abstract suspend fun deleteMessageSyncStateForAccount(id: String)

    @Transaction
    open suspend fun deleteAccount(id: String) {
        deleteCoursesForAccount(id)
        deleteSectionsForAccount(id)
        deleteGradesForAccount(id)
        deleteEventsForAccount(id)
        deleteNotificationsForAccount(id)
        deleteConversationMembersForAccount(id)
        deleteMessagesForAccount(id)
        deleteMessageDraftsForAccount(id)
        deleteMessageSyncStateForAccount(id)
        deleteConversationsForAccount(id)
        deleteAccountRow(id)
    }

    @Query("SELECT * FROM courses WHERE accountId = :accountId ORDER BY fullName COLLATE NOCASE")
    abstract fun observeCourses(accountId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE accountId = :accountId ORDER BY fullName COLLATE NOCASE")
    abstract suspend fun getCourses(accountId: String): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCourses(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE accountId = :accountId")
    protected abstract suspend fun clearCourses(accountId: String)

    @Transaction
    open suspend fun replaceCourses(accountId: String, courses: List<CourseEntity>) {
        clearCourses(accountId)
        insertCourses(courses)
    }

    @Query("SELECT * FROM sections WHERE accountId = :accountId AND courseId = :courseId ORDER BY position")
    abstract fun observeSections(accountId: String, courseId: Long): Flow<List<SectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSections(sections: List<SectionEntity>)

    @Query("DELETE FROM sections WHERE accountId = :accountId AND courseId = :courseId")
    protected abstract suspend fun clearSections(accountId: String, courseId: Long)

    @Transaction
    open suspend fun replaceSections(accountId: String, courseId: Long, sections: List<SectionEntity>) {
        clearSections(accountId, courseId)
        insertSections(sections)
    }

    @Query("SELECT * FROM grades WHERE accountId = :accountId ORDER BY courseId, itemId")
    abstract fun observeGrades(accountId: String): Flow<List<GradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertGrades(grades: List<GradeEntity>)

    @Query("DELETE FROM grades WHERE accountId = :accountId")
    protected abstract suspend fun clearGrades(accountId: String)

    @Transaction
    open suspend fun replaceGrades(accountId: String, grades: List<GradeEntity>) {
        clearGrades(accountId)
        insertGrades(grades)
    }

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId ORDER BY startEpochSeconds")
    abstract fun observeEvents(accountId: String): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE accountId = :accountId")
    protected abstract suspend fun clearEvents(accountId: String)

    @Transaction
    open suspend fun replaceEvents(accountId: String, events: List<CalendarEventEntity>) {
        clearEvents(accountId)
        insertEvents(events)
    }

    @Query("SELECT * FROM notifications WHERE accountId = :accountId ORDER BY createdAt DESC")
    abstract fun observeNotifications(accountId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNotifications(notifications: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE accountId = :accountId AND locallyNotified = 0 AND read = 0")
    abstract suspend fun getUnannouncedNotifications(accountId: String): List<NotificationEntity>

    @Query("SELECT notificationId FROM notifications WHERE accountId = :accountId")
    abstract suspend fun getKnownNotificationIds(accountId: String): List<Long>

    @Query("UPDATE notifications SET locallyNotified = 1 WHERE accountId = :accountId AND notificationId IN (:ids)")
    abstract suspend fun markLocallyNotified(accountId: String, ids: List<Long>)

    @Query("UPDATE notifications SET read = 1 WHERE accountId = :accountId AND notificationId = :notificationId")
    abstract suspend fun markNotificationRead(accountId: String, notificationId: Long)

    @Query(
        "SELECT * FROM conversations WHERE accountId = :accountId " +
            "ORDER BY CASE WHEN unreadCount > 0 THEN 0 ELSE 1 END, latestMessageAt DESC",
    )
    abstract fun observeConversations(accountId: String): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations WHERE accountId = :accountId")
    abstract suspend fun getConversationCount(accountId: String): Int

    @Query("SELECT * FROM conversations WHERE accountId = :accountId AND conversationId = :conversationId")
    abstract suspend fun getConversation(accountId: String, conversationId: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE accountId = :accountId")
    protected abstract suspend fun clearConversations(accountId: String)

    @Transaction
    open suspend fun replaceConversations(accountId: String, conversations: List<ConversationEntity>) {
        clearConversations(accountId)
        insertConversations(conversations)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertConversationMembers(members: List<ConversationMemberEntity>)

    @Query("DELETE FROM conversation_members WHERE accountId = :accountId AND conversationId = :conversationId")
    protected abstract suspend fun clearConversationMembers(accountId: String, conversationId: Long)

    @Transaction
    open suspend fun replaceConversationMembers(
        accountId: String,
        conversationId: Long,
        members: List<ConversationMemberEntity>,
    ) {
        clearConversationMembers(accountId, conversationId)
        insertConversationMembers(members)
    }

    @Query(
        "SELECT * FROM messages WHERE accountId = :accountId AND conversationId = :conversationId " +
            "ORDER BY createdAt, messageId",
    )
    abstract fun observeMessages(accountId: String, conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE accountId = :accountId")
    abstract suspend fun getMessageCount(accountId: String): Int

    @Query("SELECT messageId FROM messages WHERE accountId = :accountId")
    abstract suspend fun getKnownMessageIds(accountId: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query(
        "DELETE FROM messages WHERE accountId = :accountId AND conversationId = :conversationId " +
            "AND messageId NOT IN (SELECT messageId FROM messages WHERE accountId = :accountId " +
            "AND conversationId = :conversationId ORDER BY createdAt DESC, messageId DESC LIMIT :limit)",
    )
    abstract suspend fun trimMessages(accountId: String, conversationId: Long, limit: Int = 200)

    @Query(
        "SELECT * FROM messages WHERE accountId = :accountId AND locallyNotified = 0 " +
            "AND isMine = 0 ORDER BY createdAt",
    )
    abstract suspend fun getUnannouncedMessages(accountId: String): List<MessageEntity>

    @Query("UPDATE messages SET locallyNotified = 1 WHERE accountId = :accountId AND messageId IN (:ids)")
    abstract suspend fun markMessagesLocallyNotified(accountId: String, ids: List<Long>)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE accountId = :accountId AND conversationId = :conversationId")
    abstract suspend fun markConversationRead(accountId: String, conversationId: Long)

    @Query(
        "UPDATE messages SET isRead = 1 WHERE accountId = :accountId AND conversationId = :conversationId " +
            "AND isMine = 0",
    )
    abstract suspend fun markConversationMessagesRead(accountId: String, conversationId: Long)

    @Query("SELECT * FROM message_drafts WHERE accountId = :accountId AND draftKey = :draftKey")
    abstract fun observeMessageDraft(accountId: String, draftKey: String): Flow<MessageDraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessageDraft(draft: MessageDraftEntity)

    @Query("DELETE FROM message_drafts WHERE accountId = :accountId AND draftKey = :draftKey")
    abstract suspend fun deleteMessageDraft(accountId: String, draftKey: String)

    @Query("SELECT initialized FROM message_sync_state WHERE accountId = :accountId")
    abstract suspend fun getMessageSyncInitialized(accountId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessageSyncState(state: MessageSyncStateEntity)
}

@Database(
    entities = [
        SiteAccountEntity::class,
        CourseEntity::class,
        SectionEntity::class,
        GradeEntity::class,
        CalendarEventEntity::class,
        NotificationEntity::class,
        ConversationEntity::class,
        ConversationMemberEntity::class,
        MessageEntity::class,
        MessageDraftEntity::class,
        MessageSyncStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MoodleDatabase : RoomDatabase() {
    abstract fun moodleDao(): MoodleDao
}

private val functionSetType = object : TypeToken<Set<String>>() {}.type
private val htmlFeatureSetType = object : TypeToken<Set<HtmlFeature>>() {}.type
private val moduleListType = object : TypeToken<List<MoodleModule>>() {}.type
private val conversationMemberListType = object : TypeToken<List<MoodleConversationMember>>() {}.type

fun SiteAccountEntity.toDomain(gson: Gson): SiteAccount = SiteAccount(
    id = id,
    baseUrl = baseUrl,
    siteName = siteName,
    username = username,
    userId = userId,
    fullName = fullName,
    connectionMode = ConnectionMode.valueOf(connectionMode),
    capabilities = SiteCapabilities(
        gson.fromJson(functionsJson, functionSetType) ?: emptySet(),
        gson.fromJson(htmlFeaturesJson, htmlFeatureSetType) ?: emptySet(),
    ),
    authState = AuthState.valueOf(authState),
    moodleVersion = moodleVersion,
    themeFamily = themeFamily?.let(HtmlThemeFamily::valueOf),
    lastSyncEpochSeconds = lastSyncEpochSeconds,
    isActive = isActive,
)

fun SiteAccount.toEntity(gson: Gson): SiteAccountEntity = SiteAccountEntity(
    id = id,
    baseUrl = baseUrl,
    siteName = siteName,
    username = username,
    userId = userId,
    fullName = fullName,
    connectionMode = connectionMode.name,
    functionsJson = gson.toJson(capabilities.functions),
    htmlFeaturesJson = gson.toJson(capabilities.htmlFeatures),
    authState = authState.name,
    moodleVersion = moodleVersion,
    themeFamily = themeFamily?.name,
    lastSyncEpochSeconds = lastSyncEpochSeconds,
    isActive = isActive,
)

fun CourseEntity.toDomain() = MoodleCourse(courseId, shortName, fullName, summaryHtml, startDate, endDate)
fun MoodleCourse.toEntity(accountId: String) = CourseEntity(accountId, id, shortName, fullName, summaryHtml, startDate, endDate)

fun SectionEntity.toDomain(gson: Gson) = MoodleSection(
    sectionId,
    courseId,
    name,
    summaryHtml,
    position,
    gson.fromJson(modulesJson, moduleListType) ?: emptyList(),
)

fun MoodleSection.toEntity(accountId: String, gson: Gson) = SectionEntity(
    accountId,
    courseId,
    id,
    name,
    summaryHtml,
    position,
    gson.toJson(modules),
)

fun GradeEntity.toDomain() = MoodleGrade(courseId, itemId, itemName, gradeFormatted, rangeFormatted, percentageFormatted)
fun MoodleGrade.toEntity(accountId: String) = GradeEntity(accountId, courseId, itemId, itemName, gradeFormatted, rangeFormatted, percentageFormatted)

fun CalendarEventEntity.toDomain() = MoodleCalendarEvent(eventId, name, descriptionHtml, startEpochSeconds, courseId, actionUrl)
fun MoodleCalendarEvent.toEntity(accountId: String) = CalendarEventEntity(accountId, id, name, descriptionHtml, startEpochSeconds, courseId, actionUrl)

fun NotificationEntity.toDomain() = MoodleNotification(notificationId, subject, fullMessageHtml, createdAt, read, contextUrl)
fun MoodleNotification.toEntity(accountId: String, announced: Boolean = false) = NotificationEntity(
    accountId,
    id,
    subject,
    fullMessageHtml,
    createdAt,
    read,
    contextUrl,
    announced,
)

fun ConversationEntity.toDomain(gson: Gson) = MoodleConversation(
    conversationId,
    runCatching { ConversationType.valueOf(type) }.getOrDefault(ConversationType.Unknown),
    name,
    gson.fromJson(membersJson, conversationMemberListType) ?: emptyList(),
    latestMessagePreview,
    latestMessageAt,
    unreadCount,
    isFavourite,
    canReply,
)

fun MoodleConversation.toEntity(accountId: String, gson: Gson) = ConversationEntity(
    accountId,
    id,
    type.name,
    name,
    gson.toJson(members),
    latestMessagePreview,
    latestMessageAt,
    unreadCount,
    isFavourite,
    canReply,
)

fun MoodleConversationMember.toEntity(accountId: String, conversationId: Long) = ConversationMemberEntity(
    accountId,
    conversationId,
    id,
    fullName,
    isCurrentUser,
    canMessage,
)

fun MessageEntity.toDomain() = MoodleMessage(
    messageId,
    conversationId,
    senderId,
    senderName,
    bodyText,
    bodyHtml,
    createdAt,
    isMine,
    isRead,
)

fun MoodleMessage.toEntity(accountId: String, announced: Boolean) = MessageEntity(
    accountId,
    id,
    conversationId,
    senderId,
    senderName,
    bodyText,
    bodyHtml,
    createdAt,
    isMine,
    isRead,
    announced,
)

fun MessageDraftEntity.toDomain() = MessageDraft(accountId, draftKey, body, updatedAt)
fun MessageDraft.toEntity() = MessageDraftEntity(accountId, key, body, updatedAt)
