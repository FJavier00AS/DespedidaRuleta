package com.example.despedidaruleta.data.session

import com.example.despedidaruleta.core.firebase.await
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.CodeAlreadyReservedException
import com.example.despedidaruleta.domain.model.CreatedSession
import com.example.despedidaruleta.domain.model.InvalidJoinCodeException
import com.example.despedidaruleta.domain.model.JoinedSession
import com.example.despedidaruleta.domain.model.RegeneratedJoinCode
import com.example.despedidaruleta.domain.model.RealtimeValue
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.model.SessionJoinLimitException
import com.example.despedidaruleta.domain.model.SessionMember
import com.example.despedidaruleta.domain.model.SessionNotActiveException
import com.example.despedidaruleta.domain.model.SessionNotFoundException
import com.example.despedidaruleta.domain.model.SessionRole
import com.example.despedidaruleta.domain.model.SessionSettingsDraft
import com.example.despedidaruleta.domain.model.SessionStatus
import com.example.despedidaruleta.domain.model.SessionSummary
import com.example.despedidaruleta.domain.repository.SessionRepository
import com.example.despedidaruleta.domain.usecase.JoinCodeGenerator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date
import java.util.TimeZone

class FirebaseSessionRepository(
    private val firestore: FirebaseFirestore,
    private val joinCodeGenerator: JoinCodeGenerator
) : SessionRepository {
    override fun observeUserSessions(uid: String): Flow<RealtimeValue<List<SessionSummary>>> = callbackFlow {
        val registration = firestore.collection(USERS)
            .document(uid)
            .collection(SESSION_REFS)
            .orderBy(UPDATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val value = snapshot?.documents.orEmpty().mapNotNull { it.toSessionSummary() }
                trySend(RealtimeValue(value, snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override fun observeSession(sessionId: String, uid: String): Flow<RealtimeValue<SessionDetail?>> = callbackFlow {
        var sessionSnapshot: DocumentSnapshot? = null
        var membersSnapshot: QuerySnapshot? = null
        var sessionFromCache = false
        var membersFromCache = false

        fun emitIfReady() {
            val session = sessionSnapshot ?: return
            val members = membersSnapshot ?: return
            val detail = session.toSessionDetail(
                uid = uid,
                members = members.documents.mapNotNull { it.toSessionMember() }
            )
            trySend(RealtimeValue(detail, sessionFromCache || membersFromCache))
        }

        val sessionRegistration = firestore.collection(SESSIONS)
            .document(sessionId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                sessionSnapshot = snapshot
                sessionFromCache = snapshot?.metadata?.isFromCache == true
                emitIfReady()
            }

        val membersRegistration = firestore.collection(SESSIONS)
            .document(sessionId)
            .collection(MEMBERS)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                membersSnapshot = snapshot
                membersFromCache = snapshot?.metadata?.isFromCache == true
                emitIfReady()
            }

        awaitClose {
            sessionRegistration.remove()
            membersRegistration.remove()
        }
    }

    override suspend fun createSession(
        owner: AuthUser,
        eventName: String,
        groomName: String
    ): CreatedSession {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = joinCodeGenerator.generate()
            try {
                return reserveSessionWithCode(
                    owner = owner,
                    eventName = eventName.trim(),
                    groomName = groomName.trim(),
                    code = code
                )
            } catch (_: CodeAlreadyReservedException) {
                // Try a different numeric code. The transaction keeps each attempt atomic.
            }
        }
        throw SessionJoinLimitException()
    }

    override suspend fun joinSession(user: AuthUser, code: String): JoinedSession {
        val normalizedCode = code.trim()
        val codeRef = firestore.collection(JOIN_CODES).document(normalizedCode)

        return firestore.runTransaction { transaction ->
            val codeSnapshot = transaction.get(codeRef)
            if (!codeSnapshot.exists() || codeSnapshot.getBoolean(ACTIVE) != true) {
                throw InvalidJoinCodeException()
            }

            val sessionId = codeSnapshot.getString(SESSION_ID).orEmpty()
            val sessionRef = firestore.collection(SESSIONS).document(sessionId)
            val sessionSnapshot = transaction.get(sessionRef)
            if (!sessionSnapshot.exists()) throw SessionNotFoundException()
            if (SessionStatus.fromFirestore(sessionSnapshot.getString(STATUS)) != SessionStatus.ACTIVE) {
                throw SessionNotActiveException()
            }

            val memberRef = sessionRef.collection(MEMBERS).document(user.uid)
            val userRef = firestore.collection(USERS).document(user.uid)
            val userSessionRef = userRef.collection(SESSION_REFS).document(sessionId)
            val memberSnapshot = transaction.get(memberRef)
            val userSnapshot = transaction.get(userRef)
            val ownerUid = sessionSnapshot.getString(OWNER_UID).orEmpty()
            val role = if (ownerUid == user.uid) SessionRole.OWNER else SessionRole.MEMBER
            val eventName = sessionSnapshot.getString(EVENT_NAME).orEmpty()
            val groomName = sessionSnapshot.getString(GROOM_NAME).orEmpty()

            upsertUser(transaction, userRef.path, userSnapshot.exists(), user)

            if (!memberSnapshot.exists() || memberSnapshot.getBoolean(ACTIVE) != true) {
                transaction.set(
                    memberRef,
                    memberMap(user = user, role = role, code = normalizedCode)
                )
            }

            transaction.set(
                userSessionRef,
                sessionRefMap(
                    sessionId = sessionId,
                    eventName = eventName,
                    groomName = groomName,
                    role = role,
                    code = normalizedCode
                )
            )

            JoinedSession(sessionId = sessionId, joinCode = normalizedCode)
        }.await()
    }

    override suspend fun updateSessionSettings(
        user: AuthUser,
        sessionId: String,
        settings: SessionSettingsDraft
    ) {
        val sessionRef = firestore.collection(SESSIONS).document(sessionId)
        val userSessionRef = firestore.collection(USERS)
            .document(user.uid)
            .collection(SESSION_REFS)
            .document(sessionId)

        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            if (!sessionSnapshot.exists()) throw SessionNotFoundException()
            if (sessionSnapshot.getString(OWNER_UID) != user.uid) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "Only the owner can update session settings.",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                )
            }

            val update = mapOf(
                EVENT_NAME to settings.eventName,
                GROOM_NAME to settings.groomName,
                GROOM_PHOTO_URL to settings.groomPhotoUrl,
                STARTS_AT to settings.startsAtMillis?.let { Date(it) },
                ENDS_AT to settings.endsAtMillis?.let { Date(it) },
                TIME_ZONE to settings.timeZone,
                UPDATED_AT to FieldValue.serverTimestamp()
            )
            transaction.update(sessionRef, update)
            transaction.set(
                userSessionRef,
                mapOf(
                    SESSION_ID to sessionId,
                    EVENT_NAME to settings.eventName,
                    GROOM_NAME to settings.groomName,
                    JOIN_CODE to sessionSnapshot.getString(JOIN_CODE).orEmpty(),
                    ROLE to SessionRole.OWNER.firestoreValue,
                    STATUS to SessionStatus.fromFirestore(sessionSnapshot.getString(STATUS)).firestoreValue,
                    "joinedWithCode" to sessionSnapshot.getString(JOIN_CODE).orEmpty(),
                    UPDATED_AT to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }.await()
    }

    override suspend fun regenerateJoinCode(user: AuthUser, sessionId: String): RegeneratedJoinCode {
        repeat(MAX_CODE_ATTEMPTS) {
            val newCode = joinCodeGenerator.generate()
            try {
                return reserveReplacementCode(user = user, sessionId = sessionId, newCode = newCode)
            } catch (_: CodeAlreadyReservedException) {
                // Try another six-digit code. The reservation itself stays transactional.
            }
        }
        throw SessionJoinLimitException()
    }

    private suspend fun reserveSessionWithCode(
        owner: AuthUser,
        eventName: String,
        groomName: String,
        code: String
    ): CreatedSession {
        val sessionRef = firestore.collection(SESSIONS).document()
        val codeRef = firestore.collection(JOIN_CODES).document(code)
        val memberRef = sessionRef.collection(MEMBERS).document(owner.uid)
        val userRef = firestore.collection(USERS).document(owner.uid)
        val userSessionRef = userRef.collection(SESSION_REFS).document(sessionRef.id)

        return firestore.runTransaction { transaction ->
            val codeSnapshot = transaction.get(codeRef)
            val userSnapshot = transaction.get(userRef)
            if (codeSnapshot.exists()) throw CodeAlreadyReservedException()

            upsertUser(transaction, userRef.path, userSnapshot.exists(), owner)
            transaction.set(
                sessionRef,
                mapOf(
                    EVENT_NAME to eventName,
                    GROOM_NAME to groomName,
                    GROOM_PHOTO_URL to null,
                    STARTS_AT to null,
                    ENDS_AT to null,
                    JOIN_CODE to code,
                    OWNER_UID to owner.uid,
                    OWNER_DISPLAY_NAME to owner.displayName,
                    STATUS to SessionStatus.ACTIVE.firestoreValue,
                    TIME_ZONE to TimeZone.getDefault().id,
                    MEMBER_COUNT to 1,
                    CREATED_AT to FieldValue.serverTimestamp(),
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                codeRef,
                mapOf(
                    SESSION_ID to sessionRef.id,
                    ACTIVE to true,
                    CREATED_AT to FieldValue.serverTimestamp(),
                    CREATED_BY to owner.uid
                )
            )
            transaction.set(memberRef, memberMap(user = owner, role = SessionRole.OWNER, code = code))
            transaction.set(
                userSessionRef,
                sessionRefMap(
                    sessionId = sessionRef.id,
                    eventName = eventName,
                    groomName = groomName,
                    role = SessionRole.OWNER,
                    code = code
                )
            )

            CreatedSession(sessionId = sessionRef.id, joinCode = code)
        }.await()
    }

    private suspend fun reserveReplacementCode(
        user: AuthUser,
        sessionId: String,
        newCode: String
    ): RegeneratedJoinCode {
        val sessionRef = firestore.collection(SESSIONS).document(sessionId)
        val newCodeRef = firestore.collection(JOIN_CODES).document(newCode)
        val ownerSessionRef = firestore.collection(USERS)
            .document(user.uid)
            .collection(SESSION_REFS)
            .document(sessionId)

        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            val newCodeSnapshot = transaction.get(newCodeRef)
            if (!sessionSnapshot.exists()) throw SessionNotFoundException()
            if (newCodeSnapshot.exists()) throw CodeAlreadyReservedException()
            if (sessionSnapshot.getString(OWNER_UID) != user.uid) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "Only the owner can regenerate the join code.",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                )
            }
            val oldCode = sessionSnapshot.getString(JOIN_CODE).orEmpty()
            if (oldCode.isNotBlank()) {
                val oldCodeRef = firestore.collection(JOIN_CODES).document(oldCode)
                transaction.update(
                    oldCodeRef,
                    mapOf(
                        ACTIVE to false,
                        UPDATED_AT to FieldValue.serverTimestamp()
                    )
                )
            }
            transaction.set(
                newCodeRef,
                mapOf(
                    SESSION_ID to sessionId,
                    ACTIVE to true,
                    CREATED_AT to FieldValue.serverTimestamp(),
                    CREATED_BY to user.uid
                )
            )
            transaction.update(
                sessionRef,
                mapOf(
                    JOIN_CODE to newCode,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                ownerSessionRef,
                mapOf(
                    JOIN_CODE to newCode,
                    "joinedWithCode" to newCode,
                    UPDATED_AT to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            RegeneratedJoinCode(sessionId = sessionId, oldCode = oldCode, newCode = newCode)
        }.await()
    }

    private fun upsertUser(
        transaction: com.google.firebase.firestore.Transaction,
        userPath: String,
        exists: Boolean,
        user: AuthUser
    ) {
        val userRef = firestore.document(userPath)
        val baseData = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "email" to user.email,
            UPDATED_AT to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp()
        )
        if (exists) {
            transaction.update(userRef, baseData)
        } else {
            transaction.set(userRef, baseData + mapOf(CREATED_AT to FieldValue.serverTimestamp()))
        }
    }

    private fun memberMap(user: AuthUser, role: SessionRole, code: String): Map<String, Any?> = mapOf(
        "uid" to user.uid,
        "displayName" to user.displayName,
        "email" to user.email,
        ROLE to role.firestoreValue,
        ACTIVE to true,
        "joinedWithCode" to code,
        "joinedAt" to FieldValue.serverTimestamp(),
        UPDATED_AT to FieldValue.serverTimestamp()
    )

    private fun sessionRefMap(
        sessionId: String,
        eventName: String,
        groomName: String,
        role: SessionRole,
        code: String
    ): Map<String, Any?> = mapOf(
        SESSION_ID to sessionId,
        EVENT_NAME to eventName,
        GROOM_NAME to groomName,
        JOIN_CODE to code,
        ROLE to role.firestoreValue,
        STATUS to SessionStatus.ACTIVE.firestoreValue,
        "joinedWithCode" to code,
        CREATED_AT to FieldValue.serverTimestamp(),
        UPDATED_AT to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toSessionSummary(): SessionSummary? {
        val sessionId = getString(SESSION_ID) ?: id
        return SessionSummary(
            id = sessionId,
            eventName = getString(EVENT_NAME).orEmpty(),
            groomName = getString(GROOM_NAME).orEmpty(),
            role = SessionRole.fromFirestore(getString(ROLE)),
            status = SessionStatus.fromFirestore(getString(STATUS)),
            joinCode = getString(JOIN_CODE).orEmpty(),
            updatedAtMillis = getTimestampMillis(UPDATED_AT)
        )
    }

    private fun DocumentSnapshot.toSessionMember(): SessionMember? {
        val uid = getString("uid") ?: id
        return SessionMember(
            uid = uid,
            displayName = getString("displayName").orEmpty(),
            email = getString("email").orEmpty(),
            role = SessionRole.fromFirestore(getString(ROLE)),
            active = getBoolean(ACTIVE) != false
        )
    }

    private fun DocumentSnapshot.toSessionDetail(uid: String, members: List<SessionMember>): SessionDetail? {
        if (!exists()) return null
        val currentMember = members.firstOrNull { it.uid == uid && it.active } ?: return null
        val activeMembers = members.filter { it.active }
        return SessionDetail(
            id = id,
            eventName = getString(EVENT_NAME).orEmpty(),
            groomName = getString(GROOM_NAME).orEmpty(),
            groomPhotoUrl = getString(GROOM_PHOTO_URL),
            joinCode = getString(JOIN_CODE).orEmpty(),
            ownerUid = getString(OWNER_UID).orEmpty(),
            ownerDisplayName = getString(OWNER_DISPLAY_NAME).orEmpty(),
            role = currentMember.role,
            status = SessionStatus.fromFirestore(getString(STATUS)),
            timeZone = getString(TIME_ZONE).orEmpty(),
            memberCount = activeMembers.size,
            startsAtMillis = getTimestampMillis(STARTS_AT),
            endsAtMillis = getTimestampMillis(ENDS_AT),
            createdAtMillis = getTimestampMillis(CREATED_AT),
            updatedAtMillis = getTimestampMillis(UPDATED_AT),
            members = activeMembers.sortedWith(compareBy<SessionMember> { it.role != SessionRole.OWNER }.thenBy { it.displayName })
        )
    }

    private fun DocumentSnapshot.getTimestampMillis(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        else -> null
    }

    private companion object {
        const val MAX_CODE_ATTEMPTS = 20
        const val USERS = "users"
        const val SESSION_REFS = "sessionRefs"
        const val JOIN_CODES = "joinCodes"
        const val SESSIONS = "sessions"
        const val MEMBERS = "members"
        const val SESSION_ID = "sessionId"
        const val EVENT_NAME = "eventName"
        const val GROOM_NAME = "groomName"
        const val GROOM_PHOTO_URL = "groomPhotoUrl"
        const val STARTS_AT = "startsAt"
        const val ENDS_AT = "endsAt"
        const val JOIN_CODE = "joinCode"
        const val OWNER_UID = "ownerUid"
        const val OWNER_DISPLAY_NAME = "ownerDisplayName"
        const val STATUS = "status"
        const val ROLE = "role"
        const val ACTIVE = "active"
        const val TIME_ZONE = "timeZone"
        const val MEMBER_COUNT = "memberCount"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val CREATED_BY = "createdBy"
    }
}
