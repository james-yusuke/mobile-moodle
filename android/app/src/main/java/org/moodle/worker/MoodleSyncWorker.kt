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
import org.moodle.MainActivity
import org.moodle.R
import org.moodle.core.model.MoodleResult
import org.moodle.data.local.MoodleDao
import org.moodle.data.repository.MoodleRepository

@HiltWorker
class MoodleSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: MoodleDao,
    private val repository: MoodleRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var retryNeeded = false
        dao.getSyncableAccounts().forEach { account ->
            val result = repository.sync(account.id)
            if (result is MoodleResult.Failure && result.error.code !in setOf("session_expired", "invalidtoken")) {
                retryNeeded = true
            }
            announceNewNotifications(account.id)
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private suspend fun announceNewNotifications(accountId: String) {
        val notifications = dao.getUnannouncedNotifications(accountId)
        if (notifications.isEmpty()) return
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            accountId.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notifications.forEach { notification ->
            NotificationManagerCompat.from(applicationContext).notify(
                (accountId + notification.notificationId).hashCode(),
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_moodle)
                    .setContentTitle(notification.subject.ifBlank { applicationContext.getString(R.string.app_name) })
                    .setContentText(notification.fullMessageHtml.replace(Regex("<[^>]+>"), " ").trim())
                    .setStyle(NotificationCompat.BigTextStyle())
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build(),
            )
        }
        dao.markLocallyNotified(accountId, notifications.map { it.notificationId })
    }

    companion object {
        const val PERIODIC_WORK_NAME = "moodle-periodic-sync"
        const val CHANNEL_ID = "moodle-updates"
    }
}
