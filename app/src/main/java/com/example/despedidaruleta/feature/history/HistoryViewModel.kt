package com.example.despedidaruleta.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SpinRecord
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import com.example.despedidaruleta.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val spins: List<SpinRecord> = emptyList(),
    val isOwner: Boolean = false,
    val isLoading: Boolean = true,
    val actionLoading: Boolean = false,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class HistoryViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Inicia sesion de nuevo.") }
        } else {
            viewModelScope.launch {
                connectivityRepository.networkStatus.collect { status -> _uiState.update { it.copy(networkStatus = status) } }
            }
            viewModelScope.launch {
                sessionRepository.observeSession(sessionId, user.uid)
                    .catch { error -> showError(error) }
                    .collect { value ->
                        _uiState.update { it.copy(isOwner = value.data?.isOwner == true, fromCache = it.fromCache || value.fromCache) }
                    }
            }
            viewModelScope.launch {
                rouletteRepository.observeSpinHistory(sessionId)
                    .catch { error -> showError(error) }
                    .collect { value ->
                        _uiState.update {
                            it.copy(spins = value.data, isLoading = false, fromCache = it.fromCache || value.fromCache, errorMessage = null)
                        }
                    }
            }
        }
    }

    fun restore(spinId: String) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                rouletteRepository.restoreSpin(user, sessionId, spinId)
                _uiState.update { it.copy(actionLoading = false, infoMessage = "Giro restaurado.") }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
    }
}
