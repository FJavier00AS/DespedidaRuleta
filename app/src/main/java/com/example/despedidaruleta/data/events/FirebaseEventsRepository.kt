package com.example.despedidaruleta.data.events

import com.example.despedidaruleta.core.firebase.await
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.EventsContentMissingException
import com.example.despedidaruleta.domain.model.EventsState
import com.example.despedidaruleta.domain.model.RealtimeValue
import com.example.despedidaruleta.domain.model.pickNextEventId
import com.example.despedidaruleta.domain.repository.EventsRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date
import kotlin.random.Random

class FirebaseEventsRepository(
    private val firestore: FirebaseFirestore
) : EventsRepository {
    override fun observeEventsState(sessionId: String): Flow<RealtimeValue<EventsState>> = callbackFlow {
        val registration = stateRef(sessionId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(RealtimeValue(snapshot.toEventsState(), snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun setEventsActive(
        user: AuthUser,
        sessionId: String,
        active: Boolean,
        availableEventIds: List<String>
    ): String? {
        val sessionRef = sessionRef(sessionId)
        val stateRef = stateRef(sessionId)
        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val current = transaction.get(stateRef).toEventsState()
            val now = Date()

            if (!active) {
                if (!current.isActive) return@runTransaction null
                transaction.set(stateRef, mapOf(IS_ACTIVE to false, UPDATED_AT to now), SetOptions.merge())
                return@runTransaction null
            }

            if (current.isActive) return@runTransaction null
            if (availableEventIds.isEmpty()) throw EventsContentMissingException()
            val chosen = pickNextEventId(availableEventIds, previousId = null) ?: throw EventsContentMissingException()

            transaction.set(
                stateRef,
                mapOf(
                    IS_ACTIVE to true,
                    CURRENT_EVENT_ID to chosen,
                    AWAITING_COMPLETION to true,
                    NEXT_EVENT_AT_MILLIS to null,
                    HISTORY_IDS to (listOf(chosen) + current.historyIds).distinct().take(HISTORY_LIMIT),
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            chosen
        }.await()
    }

    override suspend fun markEventCompleted(user: AuthUser, sessionId: String) {
        val sessionRef = sessionRef(sessionId)
        val stateRef = stateRef(sessionId)
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val current = transaction.get(stateRef).toEventsState()
            if (!current.isActive || !current.awaitingCompletion || current.currentEventId == null) {
                return@runTransaction null
            }
            val nextAt = System.currentTimeMillis() + randomIntervalMs()
            transaction.set(
                stateRef,
                mapOf(
                    AWAITING_COMPLETION to false,
                    NEXT_EVENT_AT_MILLIS to nextAt,
                    UPDATED_AT to Date()
                ),
                SetOptions.merge()
            )
            null
        }.await()
    }

    override suspend fun tryTriggerNextEvent(
        user: AuthUser,
        sessionId: String,
        availableEventIds: List<String>
    ): String? {
        if (availableEventIds.isEmpty()) return null
        val sessionRef = sessionRef(sessionId)
        val stateRef = stateRef(sessionId)
        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val current = transaction.get(stateRef).toEventsState()
            val now = System.currentTimeMillis()
            val ready = current.isActive &&
                !current.awaitingCompletion &&
                current.nextEventAtMillis != null &&
                now >= current.nextEventAtMillis

            if (!ready) return@runTransaction null

            val chosen = pickNextEventId(availableEventIds, current.currentEventId) ?: return@runTransaction null

            transaction.set(
                stateRef,
                mapOf(
                    CURRENT_EVENT_ID to chosen,
                    AWAITING_COMPLETION to true,
                    NEXT_EVENT_AT_MILLIS to null,
                    HISTORY_IDS to (listOf(chosen) + current.historyIds).distinct().take(HISTORY_LIMIT),
                    UPDATED_AT to Date()
                ),
                SetOptions.merge()
            )
            chosen
        }.await()
    }

    private fun randomIntervalMs(): Long = Random.nextLong(EVENT_INTERVAL_MIN_MS, EVENT_INTERVAL_MAX_MS + 1)

    private fun sessionRef(sessionId: String): DocumentReference = firestore.collection(SESSIONS).document(sessionId)

    private fun stateRef(sessionId: String): DocumentReference = sessionRef(sessionId)
        .collection(STATE)
        .document(EVENTS)

    private fun assertMember(
        sessionId: String,
        sessionSnapshot: DocumentSnapshot,
        uid: String,
        transaction: com.google.firebase.firestore.Transaction
    ) {
        if (!sessionSnapshot.exists()) throw FirebaseFirestoreException("Session missing.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val memberSnapshot = transaction.get(sessionRef(sessionId).collection(MEMBERS).document(uid))
        if (!memberSnapshot.exists() || memberSnapshot.getBoolean(ACTIVE) != true) {
            throw FirebaseFirestoreException("Only active members can do this.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    private fun DocumentSnapshot?.toEventsState(): EventsState {
        if (this == null || !exists()) return EventsState()
        return EventsState(
            isActive = getBoolean(IS_ACTIVE) == true,
            currentEventId = getString(CURRENT_EVENT_ID),
            awaitingCompletion = getBoolean(AWAITING_COMPLETION) == true,
            nextEventAtMillis = getLong(NEXT_EVENT_AT_MILLIS),
            historyIds = (get(HISTORY_IDS) as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            updatedAtMillis = getTimestampMillis(UPDATED_AT)
        )
    }

    private fun DocumentSnapshot.getTimestampMillis(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    private companion object {
        const val EVENT_INTERVAL_MIN_MS = 5L * 60L * 1_000L
        const val EVENT_INTERVAL_MAX_MS = 20L * 60L * 1_000L
        const val HISTORY_LIMIT = 5

        const val SESSIONS = "sessions"
        const val MEMBERS = "members"
        const val STATE = "state"
        const val EVENTS = "events"
        const val ACTIVE = "active"

        const val IS_ACTIVE = "isActive"
        const val CURRENT_EVENT_ID = "currentEventId"
        const val AWAITING_COMPLETION = "awaitingCompletion"
        const val NEXT_EVENT_AT_MILLIS = "nextEventAtMillis"
        const val HISTORY_IDS = "historyIds"
        const val UPDATED_AT = "updatedAt"
    }
}
