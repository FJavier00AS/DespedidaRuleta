package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>
    val currentUser: AuthUser?

    suspend fun register(email: String, password: String, displayName: String): AuthUser
    suspend fun login(email: String, password: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    fun signOut()
}
