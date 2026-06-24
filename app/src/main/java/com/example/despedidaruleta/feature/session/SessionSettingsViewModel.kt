package com.example.despedidaruleta.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.SessionRepository
import com.example.despedidaruleta.domain.usecase.SessionSettingsValidators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone

enum class PhotoPreviewState {
    EMPTY,
    LOADING,
    READY,
    ERROR
}

data class SessionSettingsUiState(
    val session: SessionDetail? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isRegeneratingCode: Boolean = false,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val eventName: String = "",
    val groomName: String = "",
    val photoUrl: String = "",
    val normalizedPhotoUrl: String? = null,
    val startsAt: String = "",
    val endsAt: String = "",
    val timeZone: String = TimeZone.getDefault().id,
    val eventNameError: String? = null,
    val groomNameError: String? = null,
    val photoUrlError: String? = null,
    val startsAtError: String? = null,
    val endsAtError: String? = null,
    val timeZoneError: String? = null,
    val photoPreviewState: PhotoPreviewState = PhotoPreviewState.EMPTY,
    val saved: Boolean = false
) {
    val canEdit: Boolean = session?.isOwner == true
    val canSubmit: Boolean = !isSaving && networkStatus == NetworkStatus.ONLINE && canEdit
    val canRegenerateCode: Boolean = !isRegeneratingCode && networkStatus == NetworkStatus.ONLINE && canEdit
}

class SessionSettingsViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionSettingsUiState())
    val uiState: StateFlow<SessionSettingsUiState> = _uiState.asStateFlow()
    private var formInitialized = false

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
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
                        _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    }
                    .collect { value ->
                        val session = value.data
                        _uiState.update { current ->
                            val initialized = if (!formInitialized && session != null) {
                                formInitialized = true
                                val zone = session.timeZone.ifBlank { TimeZone.getDefault().id }
                                val normalizedUrl = session.groomPhotoUrl.orEmpty().ifBlank { null }
                                current.copy(
                                    session = session,
                                    isLoading = false,
                                    fromCache = value.fromCache,
                                    errorMessage = null,
                                    eventName = session.eventName,
                                    groomName = session.groomName,
                                    photoUrl = session.groomPhotoUrl.orEmpty(),
                                    normalizedPhotoUrl = normalizedUrl,
                                    startsAt = SessionSettingsValidators.formatDate(session.startsAtMillis, zone),
                                    endsAt = SessionSettingsValidators.formatDate(session.endsAtMillis, zone),
                                    timeZone = zone,
                                    photoPreviewState = if (normalizedUrl == null) PhotoPreviewState.EMPTY else PhotoPreviewState.LOADING
                                )
                            } else {
                                current.copy(
                                    session = session,
                                    isLoading = false,
                                    fromCache = value.fromCache,
                                    errorMessage = null
                                )
                            }
                            initialized
                        }
                    }
            }
        }
    }

    fun onEventNameChanged(value: String) {
        _uiState.update { it.copy(eventName = value, eventNameError = null, errorMessage = null, successMessage = null, saved = false) }
    }

    fun onGroomNameChanged(value: String) {
        _uiState.update { it.copy(groomName = value, groomNameError = null, errorMessage = null, successMessage = null, saved = false) }
    }

    fun onPhotoUrlChanged(value: String) {
        val normalized = SessionSettingsValidators.normalizePublicImageUrl(value)
        _uiState.update {
            it.copy(
                photoUrl = value,
                normalizedPhotoUrl = normalized.value,
                photoUrlError = normalized.error,
                photoPreviewState = when {
                    value.isBlank() -> PhotoPreviewState.EMPTY
                    normalized.error != null -> PhotoPreviewState.ERROR
                    else -> PhotoPreviewState.LOADING
                },
                errorMessage = null,
                successMessage = null,
                saved = false
            )
        }
    }

    fun onPhotoLoaded() {
        _uiState.update {
            if (it.normalizedPhotoUrl == null) it else it.copy(photoPreviewState = PhotoPreviewState.READY, photoUrlError = null)
        }
    }

    fun onPhotoLoadFailed() {
        _uiState.update {
            if (it.normalizedPhotoUrl == null) {
                it
            } else {
                it.copy(
                    photoPreviewState = PhotoPreviewState.ERROR,
                    photoUrlError = "No se ha podido cargar la imagen. Comprueba que sea publica."
                )
            }
        }
    }

    fun onStartsAtChanged(value: String) {
        _uiState.update { it.copy(startsAt = value, startsAtError = null, errorMessage = null, successMessage = null, saved = false) }
    }

    fun onEndsAtChanged(value: String) {
        _uiState.update { it.copy(endsAt = value, endsAtError = null, errorMessage = null, successMessage = null, saved = false) }
    }

    fun onTimeZoneChanged(value: String) {
        _uiState.update { it.copy(timeZone = value, timeZoneError = null, errorMessage = null, successMessage = null, saved = false) }
    }

    fun save() {
        val current = _uiState.value
        if (!current.canSubmit) return
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "La sesion de usuario ha caducado. Inicia sesion de nuevo.") }
            return
        }
        if (current.networkStatus == NetworkStatus.OFFLINE) {
            _uiState.update { it.copy(errorMessage = "Necesitas conexion para guardar la configuracion compartida.") }
            return
        }

        val validation = SessionSettingsValidators.validate(
            eventName = current.eventName,
            groomName = current.groomName,
            rawPhotoUrl = current.photoUrl,
            startsAtText = current.startsAt,
            endsAtText = current.endsAt,
            timeZone = current.timeZone
        )
        val draft = validation.draft
        if (!validation.isValid || draft == null) {
            _uiState.update {
                it.copy(
                    eventNameError = validation.eventNameError,
                    groomNameError = validation.groomNameError,
                    photoUrlError = validation.photoUrlError,
                    startsAtError = validation.startsAtError,
                    endsAtError = validation.endsAtError,
                    timeZoneError = validation.timeZoneError
                )
            }
            return
        }
        if (draft.groomPhotoUrl != null && current.photoPreviewState != PhotoPreviewState.READY) {
            _uiState.update {
                it.copy(photoUrlError = "Espera a que la previsualizacion cargue correctamente antes de guardar.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            runCatching {
                sessionRepository.updateSessionSettings(user, sessionId, draft)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        successMessage = "Configuracion guardada.",
                        saved = true
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun regenerateCode() {
        val current = _uiState.value
        if (!current.canRegenerateCode) return
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "La sesion de usuario ha caducado. Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRegeneratingCode = true, errorMessage = null, successMessage = null) }
            runCatching {
                sessionRepository.regenerateJoinCode(user, sessionId)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isRegeneratingCode = false,
                        successMessage = "Codigo regenerado: ${result.newCode}. El codigo anterior queda inactivo."
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isRegeneratingCode = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun navigationConsumed() {
        _uiState.update { it.copy(saved = false) }
    }
}
