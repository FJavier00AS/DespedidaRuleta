package com.example.despedidaruleta.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.despedidaruleta.MainActivity
import com.example.despedidaruleta.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DespedidaMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data[KEY_TITLE]?.takeIf { it.isNotBlank() } ?: "Despedida Ruleta"
        val body = message.data[KEY_BODY].orEmpty()
        val sessionId = message.data[KEY_SESSION_ID]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationChannels.ensureRemindersChannel(applicationContext)
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!sessionId.isNullOrBlank()) putExtra(SessionReminderWorker.EXTRA_SESSION_ID, sessionId)
        }
        val notificationId = (sessionId ?: title).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_SESSION_ID = "sessionId"
    }
}
