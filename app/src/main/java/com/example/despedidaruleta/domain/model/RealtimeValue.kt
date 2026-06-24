package com.example.despedidaruleta.domain.model

data class RealtimeValue<T>(
    val data: T,
    val fromCache: Boolean
)

enum class NetworkStatus {
    ONLINE,
    OFFLINE
}
