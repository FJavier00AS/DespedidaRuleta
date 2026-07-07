package com.example.despedidaruleta.feature.roulette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.CategoryStats
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.GamePhase
import com.example.despedidaruleta.domain.model.LocalUserSettings
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.RouletteCategory
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
    val totalAvailable: Int = stats.sumOf { it.availableCount }
    val totalUsed: Int = stats.sumOf { it.usedCount }
    val categorySpinInProgress: Boolean = gameState.phase == GamePhase.CATEGORY_SPINNING
    val contentSpinInProgress: Boolean = gameState.phase == GamePhase.CONTENT_SPINNING
    val canSpinCategory: Boolean = !actionLoading && !categorySpinInProgress && !contentSpinInProgress && totalAvailable > 0
    val selectedCategoryAvailable: Int = stats.firstOrNull { it.category == gameState.selectedCategory }?.availableCount ?: 0
    val canSpinContent: Boolean = !actionLoading &&
        gameState.phase == GamePhase.CATEGORY_SELECTED &&
        gameState.selectedCategory != null &&
        selectedCategoryAvailable > 0
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

    fun spinCategory() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val category = rouletteRepository.startCategorySpin(user, sessionId)
                _uiState.update { it.copy(infoMessage = "Ruleta de ${category.label.lowercase()} lista.") }
                delay(2_500)
                rouletteRepository.markCategorySpinCompleted(user, sessionId)
                _uiState.update { it.copy(actionLoading = false) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun openLightningSection() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                rouletteRepository.startLightningRound(user, sessionId, LIGHTNING_ROUND_SIZE)
                _uiState.update { it.copy(actionLoading = false, infoMessage = "Ronda relámpago lista. Responde rápido.") }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun closeLightningSummary() {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null) }
            try {
                rouletteRepository.closeLightningRound(user, sessionId)
                _uiState.update { it.copy(actionLoading = false, infoMessage = "Ruleta lista para otro giro.") }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun spinContent() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        val category = _uiState.value.gameState.selectedCategory
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "Primero gira la ruleta de categoria.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val spin = rouletteRepository.startContentSpin(user, sessionId, category)
                _uiState.update { it.copy(infoMessage = "Girando ${spin.category.label.lowercase()}") }
                delay(2_500)
                rouletteRepository.markSpinCompleted(user, sessionId, spin.id)
                _uiState.update { it.copy(actionLoading = false) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun resolveResult(success: Boolean) {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        val selectedCategory = _uiState.value.gameState.selectedCategory
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null, infoMessage = null) }
            try {
                if (!success && selectedCategory == RouletteCategory.QUESTION) {
                    rouletteRepository.openPunishmentWheel(user, sessionId)
                    _uiState.update { it.copy(actionLoading = false, infoMessage = "Fallo registrado. Gira la ruleta de castigos.") }
                } else if (selectedCategory == RouletteCategory.CHALLENGE) {
                    rouletteRepository.advanceLightningRound(user, sessionId, success)
                    _uiState.update { it.copy(actionLoading = false) }
                } else {
                    rouletteRepository.returnToCategoryWheel(user, sessionId)
                    val message = if (selectedCategory == RouletteCategory.QUESTION && success) {
                        PRIZE_MESSAGES.random()
                    } else {
                        "Volviendo a la ruleta principal."
                    }
                    _uiState.update { it.copy(actionLoading = false, infoMessage = message) }
                }
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

    private companion object {
        const val LIGHTNING_ROUND_SIZE = 10
        val PRIZE_MESSAGES = listOf(
            "¡Premio! Respuesta correcta. Cobra tu billete falso del banco.",
            "¡Acertaste! El banco te debe un billete de mentira.",
            "¡Premio! Que alguien te pague en dinero falso, te lo has ganado.",
            "¡Correcto! Reclama tu recompensa (de mentira) ya mismo.",
            "¡Premio! Un billete falso más para tu colección."
        )
    }
}
