package com.example.despedidaruleta.feature.lightning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LightningRoundState(
    val questions: List<ContentItem>,
    val currentIndex: Int = 0,
    val secondsLeft: Int = LightningViewModel.SECONDS_PER_QUESTION,
    val hits: Int = 0,
    val finished: Boolean = false
) {
    val currentQuestion: ContentItem? = questions.getOrNull(currentIndex)
    val totalQuestions: Int = questions.size
}

data class LightningUiState(
    val round: LightningRoundState? = null,
    val availableCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val actionLoading: Boolean = false,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null
) {
    val alreadyPlayed: Boolean = round == null && totalCount > 0 && availableCount == 0
    val canStart: Boolean = round == null && !actionLoading && availableCount > 0
}

class LightningViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LightningUiState())
    val uiState: StateFlow<LightningUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
        viewModelScope.launch {
            rouletteRepository.observeCategoryStats(sessionId)
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                }
                .collect { value ->
                    val stats = value.data.firstOrNull { it.category == RouletteCategory.LIGHTNING }
                    _uiState.update {
                        it.copy(
                            availableCount = stats?.availableCount ?: 0,
                            totalCount = stats?.totalCount ?: 0,
                            fromCache = it.fromCache || value.fromCache,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun startRound() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        if (_uiState.value.round != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, errorMessage = null) }
            try {
                val questions = rouletteRepository.drawLightningQuestions(user, sessionId)
                _uiState.update { it.copy(actionLoading = false, round = LightningRoundState(questions = questions)) }
                restartTimer()
            } catch (error: Throwable) {
                _uiState.update { it.copy(actionLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun answer(correct: Boolean) {
        val round = _uiState.value.round ?: return
        if (round.finished) return
        timerJob?.cancel()
        val hits = round.hits + if (correct) 1 else 0
        val nextIndex = round.currentIndex + 1
        if (nextIndex >= round.totalQuestions) {
            _uiState.update { it.copy(round = round.copy(hits = hits, finished = true)) }
        } else {
            _uiState.update {
                it.copy(round = round.copy(hits = hits, currentIndex = nextIndex, secondsLeft = SECONDS_PER_QUESTION))
            }
            restartTimer()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun restartTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val round = _uiState.value.round ?: return@launch
                if (round.finished) return@launch
                if (round.secondsLeft <= 1) {
                    answer(false)
                    return@launch
                }
                _uiState.update { it.copy(round = round.copy(secondsLeft = round.secondsLeft - 1)) }
            }
        }
    }

    companion object {
        const val SECONDS_PER_QUESTION = 10
    }
}
