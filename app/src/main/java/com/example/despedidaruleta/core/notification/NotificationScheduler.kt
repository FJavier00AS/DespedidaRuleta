package com.example.despedidaruleta.core.notification

import com.example.despedidaruleta.domain.model.LocalUserSettings

interface NotificationScheduler {
    fun sync(settings: LocalUserSettings)
    fun cancel()
}
