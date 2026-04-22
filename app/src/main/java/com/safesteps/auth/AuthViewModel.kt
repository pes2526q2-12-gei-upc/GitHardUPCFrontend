package com.safesteps.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.enviarUsuarioGoogleAlBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var lastSyncedGoogleUserId: String? = null

    fun onUserLoggedIn(user: UserInfo) {
        _uiState.update { it.copy(currentUser = user) }
        sincronizarUsuarioConBackend(user)
    }

    fun restoreLoggedUser(user: UserInfo?) {
        _uiState.update { it.copy(currentUser = user) }
        if (user == null) {
            lastSyncedGoogleUserId = null
            return
        }

        sincronizarUsuarioConBackend(user)
    }

    fun onLogout() {
        lastSyncedGoogleUserId = null
        _uiState.update { it.copy(currentUser = null) }
    }

    private fun sincronizarUsuarioConBackend(user: UserInfo) {
        val syncKey = user.googleId.ifBlank { user.email }
        if (syncKey.isBlank() || lastSyncedGoogleUserId == syncKey) {
            return
        }

        lastSyncedGoogleUserId = syncKey

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    enviarUsuarioGoogleAlBackend(user)
                }
            }.onFailure { error ->
                lastSyncedGoogleUserId = null
                Log.e(
                    "AUTH_VIEW_MODEL",
                    "No se pudo notificar el inicio de sesion de Google al backend",
                    error
                )
            }
        }
    }
}
