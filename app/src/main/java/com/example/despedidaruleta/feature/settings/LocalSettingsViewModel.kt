package com.example.despedidaruleta.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.notification.NotificationScheduler
import com.example.despedidaruleta.domain.model.LocalUserSettings
import com.example.despedidaruleta.domain.repository.LocalSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalSettingsUiState(
    val settings: LocalUserSettings = LocalUserSettings(),
    val infoMessage: String? = null
)

class LocalSettingsViewModel(
    private val sessionId: String,
    private val localSettingsRepository: LocalSettingsRepository,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalSettingsUiState())
    val uiState: StateFlow<LocalSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            localSettingsRepository.settings.collect { settings ->
                notificationScheduler.sync(settings)
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun setThisSessionActive(active: Boolean) {
        viewModelScope.launch {
            localSettingsRepository.setActiveSession(if (active) sessionId else null)
            _uiState.update { it.copy(infoMessage = if (active) "Sesion activa para avisos." else "Sesion desactivada para avisos.") }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            localSettingsRepository.setNotificationsEnabled(enabled)
            if (!enabled) notificationScheduler.cancel()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { localSettingsRepository.setSoundEnabled(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { localSettingsRepository.setHapticEnabled(enabled) }
    }

    fun setVisualEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { localSettingsRepository.setVisualEffectsEnabled(enabled) }
    }

    fun setQuietStart(hour: Int) {
        val current = _uiState.value.settings
        viewModelScope.launch { localSettingsRepository.setQuietHours(hour, current.quietHoursEnd) }
    }

    fun setQuietEnd(hour: Int) {
        val current = _uiState.value.settings
        viewModelScope.launch { localSettingsRepository.setQuietHours(current.quietHoursStart, hour) }
    }
}
