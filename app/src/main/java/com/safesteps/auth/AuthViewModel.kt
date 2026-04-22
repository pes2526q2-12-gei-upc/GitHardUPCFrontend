package com.safesteps.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.sincronizarUsuarioConBackend as sincronizarUsuarioConBackendApi
import com.safesteps.data.UserSyncResult
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
    private var blockedRestoreGoogleUserId: String? = null
    private var syncingGoogleUserId: String? = null
    private var nextBannerId = 0L

    fun onUserLoggedIn(user: UserInfo) {
        blockedRestoreGoogleUserId = null
        sincronizarUsuarioConBackendAsync(user)
    }

    fun restoreLoggedUser(user: UserInfo?) {
        if (user == null) {
            blockedRestoreGoogleUserId = null
            syncingGoogleUserId = null
            _uiState.update { it.copy(currentUser = null) }
            return
        }

        if (_uiState.value.currentUser?.googleId == user.googleId) {
            return
        }

        if (blockedRestoreGoogleUserId == user.googleId) {
            return
        }

        sincronizarUsuarioConBackendAsync(user)
    }

    fun onLogout() {
        blockedRestoreGoogleUserId = null
        syncingGoogleUserId = null
        _uiState.update { it.copy(currentUser = null, authNotice = null) }
    }

    fun clearAuthNotice(noticeId: Long) {
        _uiState.update { state ->
            if (state.authNotice?.id == noticeId) {
                state.copy(authNotice = null)
            } else {
                state
            }
        }
    }

    private fun sincronizarUsuarioConBackendAsync(user: UserInfo) {
        val syncKey = user.googleId.ifBlank { user.email }
        if (syncKey.isBlank() || syncingGoogleUserId == syncKey) {
            return
        }

        syncingGoogleUserId = syncKey

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    sincronizarUsuarioConBackendApi(user)
                }
            }.onSuccess { result ->
                syncingGoogleUserId = null
                blockedRestoreGoogleUserId = null
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        authNotice = createNotice(result)
                    )
                }
            }.onFailure { error ->
                syncingGoogleUserId = null
                blockedRestoreGoogleUserId = syncKey
                _uiState.update {
                    it.copy(
                        currentUser = null,
                        authNotice = createNotice(AuthNoticeMessage.SERVER_ERROR)
                    )
                }
                Log.e(
                    "AUTH_VIEW_MODEL",
                    "No se pudo sincronizar el usuario autenticado con el backend",
                    error
                )
            }
        }
    }

    private fun createNotice(result: UserSyncResult): AuthNotice {
        return when (result) {
            UserSyncResult.EXISTING_USER_UPDATED -> {
                createNotice(AuthNoticeMessage.LOGIN_SUCCESS)
            }

            UserSyncResult.NEW_USER_CREATED -> {
                createNotice(AuthNoticeMessage.REGISTER_SUCCESS)
            }
        }
    }

    private fun createNotice(
        message: AuthNoticeMessage
    ): AuthNotice {
        nextBannerId += 1
        return AuthNotice(
            id = nextBannerId,
            message = message
        )
    }
}
