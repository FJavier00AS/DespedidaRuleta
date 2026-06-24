package com.example.despedidaruleta.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.despedidaruleta.domain.model.LocalUserSettings
import java.util.concurrent.TimeUnit

class AndroidNotificationScheduler(
    private val context: Context
) : NotificationScheduler {
    override fun sync(settings: LocalUserSettings) {
        val sessionId = settings.activeSessionId
        if (!settings.notificationsEnabled || sessionId.isNullOrBlank()) {
            cancel()
            return
        }

        val request = PeriodicWorkRequestBuilder<SessionReminderWorker>(15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    SessionReminderWorker.KEY_SESSION_ID to sessionId,
                    SessionReminderWorker.KEY_QUIET_START to settings.quietHoursStart,
                    SessionReminderWorker.KEY_QUIET_END to settings.quietHoursEnd,
                    SessionReminderWorker.KEY_SOUND to settings.soundEnabled,
                    SessionReminderWorker.KEY_HAPTIC to settings.hapticEnabled
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "despedida_ruleta_session_reminder"
    }
}
