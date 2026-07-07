package com.example.despedidaruleta.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.core.navigation.AppRoutes
import com.example.despedidaruleta.core.notification.NotificationRelayClient
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.EventsState
import com.example.despedidaruleta.domain.model.LocalUserSettings
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.EventsRepository
import com.example.despedidaruleta.domain.repository.LocalSettingsRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EventsUiState(
    val isActive: Boolean = false,
    val awaitingCompletion: Boolean = false,
    val currentEvent: ContentItem? = null,
    val history: List<ContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val settings: LocalUserSettings = LocalUserSettings()
)

class EventsViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    private val eventsRepository: EventsRepository,
    localSettingsRepository: LocalSettingsRepository,
    private val notificationRelayClient: NotificationRelayClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var availableEvents: List<ContentItem> = emptyList()
    private var latestState: EventsState = EventsState()

    init {
        viewModelScope.launch {
            localSettingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            rouletteRepository.observeContent(sessionId)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "No se pudieron cargar los eventos") }
                }
                .collect { value ->
                    availableEvents = value.data.filter { it.category == RouletteCategory.EVENT && it.active && !it.used }
                    applyDisplay()
                }
        }
        viewModelScope.launch {
            eventsRepository.observeEventsState(sessionId)
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "No se pudo sincronizar el estado de eventos") }
                }
                .collect { value ->
                    latestState = value.data
                    applyDisplay()
                    manageTicker()
                }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
    }

    private fun applyDisplay() {
        val state = latestState
        val currentEvent = state.currentEventId?.let { id -> availableEvents.firstOrNull { it.id == id } }
        val history = state.historyIds.mapNotNull { id -> availableEvents.firstOrNull { it.id == id } }
        _uiState.update {
            it.copy(
                isActive = state.isActive,
                awaitingCompletion = state.awaitingCompletion,
                currentEvent = currentEvent,
                history = history,
                isLoading = false
            )
        }
    }

    fun toggleActive() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        val turningOn = !_uiState.value.isActive
        viewModelScope.launch {
            runCatching {
                eventsRepository.setEventsActive(user, sessionId, turningOn, availableEvents.map { it.id })
            }.onSuccess { chosenId ->
                if (chosenId != null) {
                    availableEvents.firstOrNull { it.id == chosenId }?.let { showEventNotification(it) }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun markCurrentEventCompleted() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                eventsRepository.markEventCompleted(user, sessionId)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            }
        }
    }

    private fun manageTicker() {
        if (!latestState.isActive) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val user = authRepository.currentUser ?: continue
                val triggeredId = runCatching {
                    eventsRepository.tryTriggerNextEvent(user, sessionId, availableEvents.map { it.id })
                }.getOrNull()
                if (triggeredId != null) {
                    availableEvents.firstOrNull { it.id == triggeredId }?.let { showEventNotification(it) }
                }
            }
        }
    }

    private fun showEventNotification(event: ContentItem) {
        viewModelScope.launch {
            runCatching {
                notificationRelayClient.broadcast(
                    sessionId = sessionId,
                    title = "Evento aleatorio",
                    body = event.text,
                    route = AppRoutes.sessionEvents(sessionId)
                )
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "No se pudo avisar al grupo: ${error.message}") }
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ROUTE = "extra_event_route"
    }
}
