package com.safesteps.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onUserLoggedIn(user: UserInfo) {
        _uiState.update { it.copy(currentUser = user) }
    }

    fun restoreLoggedUser(user: UserInfo?) {
        _uiState.update { it.copy(currentUser = user) }
    }

    fun onLogout() {
        _uiState.update { it.copy(currentUser = null) }
    }
}
