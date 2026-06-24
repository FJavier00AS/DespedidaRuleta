package com.example.despedidaruleta.feature.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.core.common.toUserMessage
import com.example.despedidaruleta.domain.model.CategoryStats
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.ImportPreview
import com.example.despedidaruleta.domain.model.ImportResult
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.ContentImportParser
import com.example.despedidaruleta.domain.repository.RouletteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val content: List<ContentItem> = emptyList(),
    val stats: List<CategoryStats> = emptyList(),
    val preview: ImportPreview? = null,
    val result: ImportResult? = null,
    val isLoading: Boolean = true,
    val isParsing: Boolean = false,
    val isImporting: Boolean = false,
    val fromCache: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.ONLINE,
    val errorMessage: String? = null
)

class AdminViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val rouletteRepository: RouletteRepository,
    private val parser: ContentImportParser,
    connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.networkStatus.collect { status -> _uiState.update { it.copy(networkStatus = status) } }
        }
        viewModelScope.launch {
            rouletteRepository.observeContent(sessionId)
                .catch { error -> showError(error) }
                .collect { value ->
                    _uiState.update {
                        it.copy(content = value.data, fromCache = it.fromCache || value.fromCache, isLoading = false)
                    }
                }
        }
        viewModelScope.launch {
            rouletteRepository.observeCategoryStats(sessionId)
                .catch { error -> showError(error) }
                .collect { value ->
                    _uiState.update {
                        it.copy(stats = value.data, fromCache = it.fromCache || value.fromCache, isLoading = false)
                    }
                }
        }
    }

    fun parseFile(uri: Uri, fallbackCategory: RouletteCategory?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, errorMessage = null, result = null) }
            try {
                val preview = parser.parse(uri, fallbackCategory)
                _uiState.update { it.copy(isParsing = false, preview = preview) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(isParsing = false, errorMessage = "No se pudo leer el archivo: ${error.toUserMessage()}") }
            }
        }
    }

    fun confirmImport() {
        val user = authRepository.currentUser
        val preview = _uiState.value.preview
        if (user == null || preview == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                val result = rouletteRepository.importContent(user, sessionId, preview.validRows)
                _uiState.update { it.copy(isImporting = false, result = result, preview = null) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(isImporting = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun loadDemoContent() {
        val user = authRepository.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Inicia sesion de nuevo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null, result = null, preview = null) }
            try {
                val result = rouletteRepository.importContent(user, sessionId, DemoRouletteContent.rows())
                _uiState.update { it.copy(isImporting = false, result = result) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(isImporting = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun clearPreview() {
        _uiState.update { it.copy(preview = null, result = null, errorMessage = null) }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
    }
}
