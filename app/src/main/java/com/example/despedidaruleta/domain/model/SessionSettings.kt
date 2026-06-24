package com.example.despedidaruleta.domain.model

data class SessionSettingsDraft(
    val eventName: String,
    val groomName: String,
    val groomPhotoUrl: String?,
    val startsAtMillis: Long?,
    val endsAtMillis: Long?,
    val timeZone: String
)

data class RegeneratedJoinCode(
    val sessionId: String,
    val oldCode: String,
    val newCode: String
)
