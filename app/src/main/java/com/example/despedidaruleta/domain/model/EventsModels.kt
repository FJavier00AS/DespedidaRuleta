package com.example.despedidaruleta.domain.model

data class EventsState(
    val isActive: Boolean = false,
    val currentEventId: String? = null,
    val awaitingCompletion: Boolean = false,
    val nextEventAtMillis: Long? = null,
    val historyIds: List<String> = emptyList(),
    val updatedAtMillis: Long? = null
)
