package com.example.despedidaruleta.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.despedidaruleta.domain.model.LocalUserSettings
import com.example.despedidaruleta.domain.repository.LocalSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore by preferencesDataStore(name = "local_user_settings")

class DataStoreLocalSettingsRepository(
    private val context: Context
) : LocalSettingsRepository {
    override val settings: Flow<LocalUserSettings> = context.userSettingsDataStore.data.map { preferences ->
        LocalUserSettings(
            activeSessionId = preferences[ACTIVE_SESSION_ID]?.takeIf { it.isNotBlank() },
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: false,
            soundEnabled = preferences[SOUND_ENABLED] ?: true,
            hapticEnabled = preferences[HAPTIC_ENABLED] ?: true,
            visualEffectsEnabled = preferences[VISUAL_EFFECTS_ENABLED] ?: true,
            quietHoursStart = preferences[QUIET_HOURS_START] ?: 2,
            quietHoursEnd = preferences[QUIET_HOURS_END] ?: 9
        )
    }

    override suspend fun setActiveSession(sessionId: String?) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[ACTIVE_SESSION_ID] = sessionId.orEmpty()
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    override suspend fun setHapticEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[HAPTIC_ENABLED] = enabled
        }
    }

    override suspend fun setVisualEffectsEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[VISUAL_EFFECTS_ENABLED] = enabled
        }
    }

    override suspend fun setQuietHours(startHour: Int, endHour: Int) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[QUIET_HOURS_START] = startHour.coerceIn(0, 23)
            preferences[QUIET_HOURS_END] = endHour.coerceIn(0, 23)
        }
    }

    private companion object {
        val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val VISUAL_EFFECTS_ENABLED = booleanPreferencesKey("visual_effects_enabled")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
    }
}
