package com.example.despedidaruleta.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionSummary
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.SessionRepository
import com.example.despedidaruleta.domain.usecase.SessionValidators
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsListUiState(
    val user: AuthUser? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = true,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null
)

data class CreateSessionUiState(
    val eventName: String = "",
    val groomName: String = "",
    val eventNameError: String? = null,
    val groomNameError: String? = null,
    val isLoading: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null,
    val createdSessionId: String? = null
)

data class JoinSessionUiState(
    val code: String = "",
    val codeError: String? = null,
    val isLoading: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null,
    val joinedSessionId: String? = null
)

class SessionsListViewModel(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionsListUiState())
    val uiState: StateFlow<SessionsListUiState> = _uiState.asStateFlow()
    private var sessionsJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                sessionsJob?.cancel()
                if (user == null) {
                    _uiState.update { it.copy(user = null, sessions = emptyList(), isLoading = false) }
                } else {
                    _uiState.update { it.copy(user = user, isLoading = true, errorMessage = null) }
                    sessionsJob = viewModelScope.launch {
                        sessionRepository.observeUserSessions(user.uid)
                            .catch { error ->
                                _uiState.update {
                                    it.copy(isLoading = false, errorMessage = error.toUserMessage())
                                }
                            }
                            .collect { value ->
                                _uiState.update {
                                    it.copy(
                                        sessions = value.data,
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
    }

    fun signOut() {
        authRepository.signOut()
    }
}

class CreateSessionViewModel(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
    }

    fun onEventNameChanged(value: String) {
        _uiState.update { it.copy(eventName = value, eventNameError = null, errorMessage = null) }
    }

    fun onGroomNameChanged(value: String) {
        _uiState.update { it.copy(groomName = value, groomNameError = null, errorMessage = null) }
    }

    fun createSession() {
        val current = _uiState.value
        if (current.isLoading) return
        if (current.networkStatus == NetworkStatus.OFFLINE) {
            _uiState.update { it.copy(errorMessage = "Necesitas conexion para crear una sesion compartida.") }
            return
        }
        val validation = SessionValidators.validateCreateSession(current.eventName, current.groomName)
        if (!validation.isValid) {
            _uiState.update {
                it.copy(
                    eventNameError = validation.eventNameError,
                    groomNameError = validation.groomNameError
                )
            }
            return
        }
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "La sesion de usuario ha caducado. Inicia sesion de nuevo.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                sessionRepository.createSession(user, current.eventName, current.groomName)
            }.onSuccess { created ->
                _uiState.update { it.copy(isLoading = false, createdSessionId = created.sessionId) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun navigationConsumed() {
        _uiState.update { it.copy(createdSessionId = null) }
    }
}

class JoinSessionViewModel(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(JoinSessionUiState())
    val uiState: StateFlow<JoinSessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
    }

    fun onCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                code = SessionValidators.normalizeJoinCode(value),
                codeError = null,
                errorMessage = null
            )
        }
    }

    fun joinSession() {
        val current = _uiState.value
        if (current.isLoading) return
        if (current.networkStatus == NetworkStatus.OFFLINE) {
            _uiState.update { it.copy(errorMessage = "Necesitas conexion para unirte con un codigo.") }
            return
        }
        val validation = SessionValidators.validateJoinCode(current.code)
        if (!validation.isValid) {
            _uiState.update { it.copy(codeError = validation.codeError) }
            return
        }
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "La sesion de usuario ha caducado. Inicia sesion de nuevo.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                sessionRepository.joinSession(user, current.code)
            }.onSuccess { joined ->
                _uiState.update { it.copy(isLoading = false, joinedSessionId = joined.sessionId) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun navigationConsumed() {
        _uiState.update { it.copy(joinedSessionId = null) }
    }
}
