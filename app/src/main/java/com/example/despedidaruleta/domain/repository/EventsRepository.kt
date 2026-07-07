package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.EventsState
import com.example.despedidaruleta.domain.model.RealtimeValue
import kotlinx.coroutines.flow.Flow

interface EventsRepository {
    fun observeEventsState(sessionId: String): Flow<RealtimeValue<EventsState>>

    suspend fun setEventsActive(user: AuthUser, sessionId: String, active: Boolean, availableEventIds: List<String>)

    suspend fun markEventCompleted(user: AuthUser, sessionId: String)

    /**
     * Fires the next event if the session is active, no event is pending
     * completion, and the random interval has elapsed. Safe to call from
     * every device that has the events screen open: Firestore's transaction
     * retry semantics guarantee only one caller actually flips the state, so
     * only that caller gets a non-null id back and should broadcast the push
     * notification for it.
     */
    suspend fun tryTriggerNextEvent(user: AuthUser, sessionId: String, availableEventIds: List<String>): String?
}
