package com.example.despedidaruleta.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.navigation.AppRoutes
import com.example.despedidaruleta.core.notification.NotificationRelayClient
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class EventsUiState(
    val isActive: Boolean = false,
    val currentEvent: ContentItem? = null,
    val history: List<ContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
    val awaitingCompletion: Boolean = false
)

class EventsViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    private val notificationRelayClient: NotificationRelayClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var availableEvents: List<ContentItem> = emptyList()
    private var lastEventId: String? = null
    private var lastEventTimestampMs: Long = 0L
    private var nextEventTimestampMs: Long = 0L

    init {
        viewModelScope.launch {
            rouletteRepository.observeContent(sessionId)
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "No se pudieron cargar los eventos") }
                }
                .collect { value ->
                    availableEvents = value.data.filter { it.category == RouletteCategory.EVENT && it.active && !it.used }
                    val currentEvent = if (_uiState.value.isActive && !_uiState.value.awaitingCompletion) {
                        pickNextEvent(availableEvents, _uiState.value.currentEvent?.id)
                    } else {
                        _uiState.value.currentEvent
                    }
                    val nextHistory = if (currentEvent != null && !_uiState.value.history.contains(currentEvent)) {
                        listOf(currentEvent) + _uiState.value.history.take(4)
                    } else {
                        _uiState.value.history
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            currentEvent = currentEvent,
                            history = nextHistory
                        )
                    }
                    if (_uiState.value.isActive) startTicker(availableEvents)
                }
        }
    }

    fun toggleActive() {
        val nextActive = !_uiState.value.isActive
        _uiState.update { it.copy(isActive = nextActive) }

        if (!nextActive) {
            tickerJob?.cancel()
            return
        }

        viewModelScope.launch {
            val events = availableEvents.ifEmpty {
                rouletteRepository.observeContent(sessionId).firstOrNull()?.data
                    ?.filter { it.category == RouletteCategory.EVENT && it.active && !it.used }
                    .orEmpty()
            }
            if (events.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No hay eventos disponibles.") }
                return@launch
            }
            availableEvents = events
            val nextEvent = pickNextEvent(events, null)
            lastEventId = nextEvent?.id
            _uiState.update {
                it.copy(
                    currentEvent = nextEvent,
                    history = if (nextEvent != null) listOf(nextEvent) else emptyList(),
                    isPaused = false,
                    awaitingCompletion = nextEvent != null
                )
            }
            startTicker(events)
        }
    }

    fun markCurrentEventCompleted() {
        if (_uiState.value.currentEvent == null || !_uiState.value.awaitingCompletion) return
        val now = System.currentTimeMillis()
        lastEventTimestampMs = now
        nextEventTimestampMs = now + randomIntervalMs()
        _uiState.update { it.copy(awaitingCompletion = false) }
    }

    private fun startTicker(events: List<ContentItem>) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_uiState.value.isActive) {
                delay(5_000)
                if (events.isEmpty() || _uiState.value.awaitingCompletion) {
                    continue
                }

                if (System.currentTimeMillis() >= nextEventTimestampMs) {
                    val next = pickNextEvent(events, lastEventId)
                    if (next != null) {
                        lastEventId = next.id
                        showEventNotification(next)
                        _uiState.update {
                            it.copy(
                                currentEvent = next,
                                history = listOf(next) + it.history.take(4),
                                isPaused = false,
                                awaitingCompletion = true
                            )
                        }
                    }
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

    private fun randomIntervalMs(): Long = Random.nextLong(EVENT_INTERVAL_MIN_MS, EVENT_INTERVAL_MAX_MS + 1)

    companion object {
        private const val EVENT_INTERVAL_MIN_MS = 5L * 60L * 1_000L
        private const val EVENT_INTERVAL_MAX_MS = 20L * 60L * 1_000L
        const val EXTRA_EVENT_ROUTE = "extra_event_route"

        fun pickNextEvent(events: List<ContentItem>, previousId: String?): ContentItem? {
            if (events.isEmpty()) return null
            val available = events.filter { it.id != previousId }
            return if (available.isEmpty()) events.firstOrNull() else available.random()
        }
    }
}
