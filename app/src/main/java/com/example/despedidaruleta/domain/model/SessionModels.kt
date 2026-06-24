package com.example.despedidaruleta.domain.model

enum class SessionRole(val firestoreValue: String) {
    OWNER("OWNER"),
    MEMBER("MEMBER");

    val label: String
        get() = when (this) {
            OWNER -> "Propietario"
            MEMBER -> "Miembro"
        }

    companion object {
        fun fromFirestore(value: String?): SessionRole = when (value) {
            OWNER.firestoreValue -> OWNER
            else -> MEMBER
        }
    }
}

enum class SessionStatus(val firestoreValue: String) {
    ACTIVE("ACTIVE"),
    ARCHIVED("ARCHIVED"),
    DELETED("DELETED");

    val label: String
        get() = when (this) {
            ACTIVE -> "Activa"
            ARCHIVED -> "Archivada"
            DELETED -> "Eliminada"
        }

    companion object {
        fun fromFirestore(value: String?): SessionStatus = when (value) {
            ARCHIVED.firestoreValue -> ARCHIVED
            DELETED.firestoreValue -> DELETED
            else -> ACTIVE
        }
    }
}

data class SessionSummary(
    val id: String,
    val eventName: String,
    val groomName: String,
    val role: SessionRole,
    val status: SessionStatus,
    val joinCode: String,
    val updatedAtMillis: Long?
)

data class SessionMember(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: SessionRole,
    val active: Boolean
)

data class SessionDetail(
    val id: String,
    val eventName: String,
    val groomName: String,
    val groomPhotoUrl: String?,
    val joinCode: String,
    val ownerUid: String,
    val ownerDisplayName: String,
    val role: SessionRole,
    val status: SessionStatus,
    val timeZone: String,
    val memberCount: Int,
    val startsAtMillis: Long?,
    val endsAtMillis: Long?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val members: List<SessionMember>
) {
    val isOwner: Boolean = role == SessionRole.OWNER
}

data class CreatedSession(
    val sessionId: String,
    val joinCode: String
)

data class JoinedSession(
    val sessionId: String,
    val joinCode: String
)
