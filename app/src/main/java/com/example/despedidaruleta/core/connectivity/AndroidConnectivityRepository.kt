package com.example.despedidaruleta.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

class AndroidConnectivityRepository(
    context: Context
) : ConnectivityRepository {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val networkStatus: Flow<NetworkStatus> = observeNetworkStatus()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = currentStatus()
        )

    private fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentStatus())
            }

            override fun onUnavailable() {
                trySend(NetworkStatus.OFFLINE)
            }
        }

        trySend(currentStatus())
        val registered = try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            true
        } catch (_: RuntimeException) {
            trySend(NetworkStatus.OFFLINE)
            false
        }

        awaitClose {
            if (registered) {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: RuntimeException) {
                    // The platform may already have removed the callback during teardown.
                }
            }
        }
    }

    private fun currentStatus(): NetworkStatus {
        return try {
            val network = connectivityManager.activeNetwork ?: return NetworkStatus.OFFLINE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.OFFLINE
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet && isValidated) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
        } catch (_: RuntimeException) {
            NetworkStatus.OFFLINE
        }
    }
}
