package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.LocalUserSettings
import kotlinx.coroutines.flow.Flow

interface LocalSettingsRepository {
    val settings: Flow<LocalUserSettings>

    suspend fun setActiveSession(sessionId: String?)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticEnabled(enabled: Boolean)
    suspend fun setVisualEffectsEnabled(enabled: Boolean)
    suspend fun setQuietHours(startHour: Int, endHour: Int)
}
