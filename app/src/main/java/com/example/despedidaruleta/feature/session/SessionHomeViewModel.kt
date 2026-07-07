package com.example.despedidaruleta.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.core.notification.FcmTopicManager
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionHomeUiState(
    val session: SessionDetail? = null,
    val isLoading: Boolean = true,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null
)

class SessionHomeViewModel(
    sessionId: String,
    authRepository: AuthRepository,
    sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository,
    fcmTopicManager: FcmTopicManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionHomeUiState())
    val uiState: StateFlow<SessionHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }

        viewModelScope.launch {
            runCatching { fcmTopicManager.subscribe(sessionId) }
        }

        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "La sesion de usuario ha caducado. Inicia sesion de nuevo."
                )
            }
        } else {
            viewModelScope.launch {
                sessionRepository.observeSession(sessionId, user.uid)
                    .catch { error ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = error.toUserMessage())
                        }
                    }
                    .collect { value ->
                        _uiState.update {
                            it.copy(
                                session = value.data,
                                fromCache = value.fromCache,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
            }
        }
    }
}
