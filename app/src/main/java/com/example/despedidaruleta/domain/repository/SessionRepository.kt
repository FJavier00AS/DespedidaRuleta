package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.CreatedSession
import com.example.despedidaruleta.domain.model.JoinedSession
import com.example.despedidaruleta.domain.model.RegeneratedJoinCode
import com.example.despedidaruleta.domain.model.RealtimeValue
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.model.SessionSettingsDraft
import com.example.despedidaruleta.domain.model.SessionSummary
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeUserSessions(uid: String): Flow<RealtimeValue<List<SessionSummary>>>
    fun observeSession(sessionId: String, uid: String): Flow<RealtimeValue<SessionDetail?>>

    suspend fun createSession(
        owner: AuthUser,
        eventName: String,
        groomName: String
    ): CreatedSession

    suspend fun joinSession(user: AuthUser, code: String): JoinedSession

    suspend fun updateSessionSettings(
        user: AuthUser,
        sessionId: String,
        settings: SessionSettingsDraft
    )

    suspend fun regenerateJoinCode(user: AuthUser, sessionId: String): RegeneratedJoinCode

    suspend fun deleteSession(user: AuthUser, sessionId: String)
}
