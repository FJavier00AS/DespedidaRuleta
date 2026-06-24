package com.example.despedidaruleta.data.auth

import com.example.despedidaruleta.core.firebase.await
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {
    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toAuthUser())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser?
        get() = auth.currentUser?.toAuthUser()

    override suspend fun register(email: String, password: String, displayName: String): AuthUser {
        val normalizedEmail = email.normalizedEmail()
        val result = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
        val firebaseUser = requireNotNull(result.user) { "Firebase no devolvio usuario tras el registro." }
        val normalizedName = displayName.trim()
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(normalizedName)
            .build()
        firebaseUser.updateProfile(profileUpdates).await()
        val user = requireNotNull(firebaseUser.toAuthUser(displayNameOverride = normalizedName)) {
            "Firebase no devolvio email tras el registro."
        }
        ensureUserDocument(user)
        return user
    }

    override suspend fun login(email: String, password: String): AuthUser {
        val result = auth.signInWithEmailAndPassword(email.normalizedEmail(), password).await()
        val user = requireNotNull(result.user?.toAuthUser()) { "Firebase no devolvio usuario tras iniciar sesion." }
        ensureUserDocument(user)
        return user
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.normalizedEmail()).await()
    }

    private fun String.normalizedEmail(): String = trim().lowercase()

    override fun signOut() {
        auth.signOut()
    }

    private suspend fun ensureUserDocument(user: AuthUser) {
        val userRef = firestore.collection(USERS).document(user.uid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val baseData = mapOf(
                "uid" to user.uid,
                "displayName" to user.displayName,
                "email" to user.email,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp()
            )
            if (snapshot.exists()) {
                transaction.update(userRef, baseData)
            } else {
                transaction.set(
                    userRef,
                    baseData + mapOf("createdAt" to FieldValue.serverTimestamp())
                )
            }
        }.await()
    }

    private fun FirebaseUser.toAuthUser(displayNameOverride: String? = null): AuthUser? {
        val emailValue = email ?: return null
        val resolvedName = displayNameOverride
            ?: displayName
            ?: emailValue.substringBefore('@')
        return AuthUser(
            uid = uid,
            displayName = resolvedName.ifBlank { emailValue.substringBefore('@') },
            email = emailValue
        )
    }

    private companion object {
        const val USERS = "users"
    }
}
