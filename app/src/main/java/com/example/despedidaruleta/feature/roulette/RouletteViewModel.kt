package com.example.despedidaruleta.feature.roulette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.CategoryStats
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.GamePhase
import com.example.despedidaruleta.domain.model.LocalUserSettings
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.RouletteGameState
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.LocalSettingsRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouletteUiState(
    val content: List<ContentItem> = emptyList(),
    val stats: List<CategoryStats> = emptyList(),
    val gameState: RouletteGameState = RouletteGameState(),
    val settings: LocalUserSettings = LocalUserSettings(),
    val isLoading: Boolean = true,
    val actionLoading: Boolean = false,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val canSpin: Boolean = !actionLoading && gameState.phase != GamePhase.CONTENT_SPINNING && stats.sumOf { it.availableCount } > 0
    val totalAvailable: Int = stats.sumOf { it.availableCount }
    val totalUsed: Int = stats.sumOf { it.usedCount }
}

class RouletteViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    connectivityRepository: ConnectivityRepository,
    localSettingsRepository: LocalSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouletteUiState())
    val uiState: StateFlow<RouletteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            rouletteRepository.observeContent(sessionId)
                .catch { error -> showError(error) }
                .collect { value ->
                    _uiState.update {
                        it.copy(
                            content = value.data,
                            fromCache = it.fromCache || value.fromCache,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
        viewModelScope.launch {
            rouletteRepository.observeCategoryStats(sessionId)
                .catch { error -> showError(error) }
                .collect { value ->
                    _uiState.update {
                        it.copy(
                            stats = value.data,
                            fromCache = it.fromCache || value.fromCache,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
        viewModelScope.launch {
            rouletteRepository.observeGameState(sessionId)
                .catch { error -> showError(error) }
                .collect { value ->
                    _uiState.update {
                        it.copy(
                            gameState = value.data,
                            fromCache = it.fromCache || value.fromCache,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun spin() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val spin = rouletteRepository.startSpin(user, sessionId)
                _uiState.update { it.copy(actionLoading = false, infoMessage = "Giro lanzado: ${spin.category.label}") }
                delay(2_500)
                rouletteRepository.markSpinCompleted(user, sessionId, spin.id)
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun resetGame() {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null) }
            try {
                rouletteRepository.resetGame(user, sessionId)
                _uiState.update { it.copy(actionLoading = false, infoMessage = "Ruleta lista para otro giro.") }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
    }
}
