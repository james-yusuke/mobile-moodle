package org.moodle.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.moodle.MainActivity
import org.moodle.R
import org.moodle.core.model.MoodleResult
import org.moodle.data.local.AppPreferences
import org.moodle.data.local.MoodleDao
import org.moodle.data.repository.MoodleRepository

@HiltWorker
class MessageSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: MoodleDao,
    private val repository: MoodleRepository,
    private val preferences: AppPreferences,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var retryNeeded = false
        val showPreview = preferences.showMessagePreview.first()
        dao.getSyncableAccounts().forEach { account ->
            val result = repository.syncMessages(account.id)
            if (result is MoodleResult.Failure && result.error.code !in AUTH_ERRORS) retryNeeded = true
            announceMessages(account.id, showPreview)
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private suspend fun announceMessages(accountId: String, showPreview: Boolean) {
        val messages = dao.getUnannouncedMessages(accountId)
        if (messages.isEmpty()) return
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            dao.markMessagesLocallyNotified(accountId, messages.map { it.messageId })
            return
        }

        messages.forEach { message ->
            val intent = Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId)
                .putExtra(MainActivity.EXTRA_CONVERSATION_ID, message.conversationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                (accountId + message.conversationId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val content = if (showPreview) {
                message.bodyText.take(180)
            } else {
                applicationContext.getString(R.string.new_message_notification)
            }
            NotificationManagerCompat.from(applicationContext).notify(
                (accountId + message.messageId).hashCode(),
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_moodle)
                    .setContentTitle(message.senderName.ifBlank { applicationContext.getString(R.string.messages) })
                    .setContentText(content)
                    .setStyle(if (showPreview) NotificationCompat.BigTextStyle().bigText(content) else null)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build(),
            )
        }
        dao.markMessagesLocallyNotified(accountId, messages.map { it.messageId })
    }

    companion object {
        const val PERIODIC_WORK_NAME = "moodle-message-sync"
        const val STARTUP_WORK_NAME = "moodle-message-startup-sync"
        const val CHANNEL_ID = "moodle-messages"
        private val AUTH_ERRORS = setOf("session_expired", "invalidtoken", "invalid_credentials")
    }
}
