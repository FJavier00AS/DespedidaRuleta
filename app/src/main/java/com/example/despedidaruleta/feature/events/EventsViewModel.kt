package com.example.despedidaruleta.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.despedidaruleta.MainActivity
import com.example.despedidaruleta.R
import com.example.despedidaruleta.core.navigation.AppRoutes
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

data class EventsUiState(
    val isActive: Boolean = false,
    val currentEvent: ContentItem? = null,
    val history: List<ContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
    val nextEventInSeconds: Long = 0L
)

class EventsViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    private val appContext: Context
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
                    val currentEvent = if (_uiState.value.isActive) {
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
            val now = System.currentTimeMillis()
            lastEventTimestampMs = now
            nextEventTimestampMs = now + EVENT_INTERVAL_MS
            _uiState.update {
                it.copy(
                    currentEvent = nextEvent,
                    history = if (nextEvent != null) listOf(nextEvent) else emptyList(),
                    isPaused = false,
                    nextEventInSeconds = (EVENT_INTERVAL_MS / 1_000).coerceAtLeast(0)
                )
            }
            startTicker(events)
        }
    }

    private fun startTicker(events: List<ContentItem>) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_uiState.value.isActive) {
                delay(1_000)
                if (events.isEmpty()) {
                    refreshCountdown()
                    continue
                }

                val now = System.currentTimeMillis()

                if (now >= nextEventTimestampMs) {
                    val next = pickNextEvent(events, lastEventId)
                    if (next != null) {
                        lastEventId = next.id
                        lastEventTimestampMs = now
                        nextEventTimestampMs = now + EVENT_INTERVAL_MS
                        showEventNotification(next)
                        _uiState.update {
                            it.copy(
                                currentEvent = next,
                                history = listOf(next) + it.history.take(4),
                                isPaused = false,
                                nextEventInSeconds = (EVENT_INTERVAL_MS / 1_000).coerceAtLeast(0)
                            )
                        }
                    }
                } else {
                    refreshCountdown(now)
                }
            }
        }
    }

    private fun refreshCountdown(now: Long = System.currentTimeMillis()) {
        val remainingMs = if (nextEventTimestampMs > 0L) (nextEventTimestampMs - now).coerceAtLeast(0) else 0L
        _uiState.update {
            it.copy(
                nextEventInSeconds = (remainingMs / 1_000).coerceAtLeast(0),
                isPaused = false
            )
        }
    }

    private fun showEventNotification(event: ContentItem) {
        ensureNotificationChannel()
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppRoutes.SessionIdArg, sessionId)
            putExtra(EXTRA_EVENT_ROUTE, AppRoutes.sessionEvents(sessionId))
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            event.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Evento aleatorio")
            .setContentText(event.text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(event.id.hashCode(), notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Eventos aleatorios",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de eventos aleatorios activos"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "roulette_events"
        private const val EVENT_INTERVAL_MS = 30L * 60L * 1_000L
        const val EXTRA_EVENT_ROUTE = "extra_event_route"

        fun pickNextEvent(events: List<ContentItem>, previousId: String?): ContentItem? {
            if (events.isEmpty()) return null
            val available = events.filter { it.id != previousId }
            return if (available.isEmpty()) events.firstOrNull() else available.random()
        }
    }
}
