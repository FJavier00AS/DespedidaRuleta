package com.example.despedidaruleta.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.despedidaruleta.MainActivity
import com.example.despedidaruleta.R
import java.util.Calendar

class SessionReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID).orEmpty()
        if (sessionId.isBlank()) return Result.success()

        val quietStart = inputData.getInt(KEY_QUIET_START, 2).coerceIn(0, 23)
        val quietEnd = inputData.getInt(KEY_QUIET_END, 9).coerceIn(0, 23)
        if (isQuietHour(quietStart, quietEnd)) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        NotificationChannels.ensureRemindersChannel(applicationContext)
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val soundEnabled = inputData.getBoolean(KEY_SOUND, true)
        val hapticEnabled = inputData.getBoolean(KEY_HAPTIC, true)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            sessionId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Despedida Ruleta")
            .setContentText("Toca girar o preparar la siguiente ronda.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(!soundEnabled)
            .setVibrate(if (hapticEnabled) longArrayOf(0, 120) else null)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(sessionId.hashCode(), notification)
        return Result.success()
    }

    private fun isQuietHour(start: Int, end: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start == end) {
            false
        } else if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_QUIET_START = "quiet_start"
        const val KEY_QUIET_END = "quiet_end"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTIC = "haptic"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
