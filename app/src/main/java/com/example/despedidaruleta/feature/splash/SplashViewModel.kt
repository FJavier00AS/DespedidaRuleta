package com.example.despedidaruleta.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.despedidaruleta.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SplashUiState {
    data object Checking : SplashUiState
    data object Authenticated : SplashUiState
    data object Unauthenticated : SplashUiState
}

class SplashViewModel(
    authRepository: AuthRepository
) : ViewModel() {
    val uiState = authRepository.authState
        .map { user -> if (user == null) SplashUiState.Unauthenticated else SplashUiState.Authenticated }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SplashUiState.Checking
        )
}
