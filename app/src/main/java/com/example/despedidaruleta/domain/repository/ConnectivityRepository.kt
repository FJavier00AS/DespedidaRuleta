package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.NetworkStatus
import kotlinx.coroutines.flow.Flow

interface ConnectivityRepository {
    val networkStatus: Flow<NetworkStatus>
}
