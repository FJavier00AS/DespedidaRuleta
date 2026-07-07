package com.example.despedidaruleta.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDERS_CHANNEL_ID = "roulette_reminders"

    fun ensureRemindersChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(REMINDERS_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            REMINDERS_CHANNEL_ID,
            "Avisos de ruleta",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorios y avisos de la sesion activa"
        }
        manager.createNotificationChannel(channel)
    }
}
