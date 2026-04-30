package com.safesteps.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.eliminarUsuarioDelBackend
import com.safesteps.data.UserSyncOutcome
import com.safesteps.data.UserSyncResult
import com.safesteps.data.sincronizarUsuarioConBackend as sincronizarUsuarioConBackendApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val syncUser: suspend (UserInfo) -> UserSyncOutcome = ::sincronizarUsuarioConBackendApi,
    private val deleteUser: suspend (String) -> Unit = ::eliminarUsuarioDelBackend
) : ViewModel() {

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
        _uiState.update {
            it.copy(
                currentUser = null,
                authNotice = null,
                isDeletingAccount = false,
                pendingDeleteAccountSignOut = false,
                pendingAccessDeniedSignOut = false
            )
        }
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

    fun onCurrentUserLanguageChanged(googleId: String, languageTag: String) {
        if (googleId.isBlank() || languageTag.isBlank()) {
            return
        }

        _uiState.update { state ->
            val currentUser = state.currentUser
            if (currentUser == null || currentUser.googleId != googleId) {
                state
            } else {
                state.copy(
                    currentUser = currentUser.copy(backendLanguageTag = languageTag)
                )
            }
        }
    }

    private fun sincronizarUsuarioConBackendAsync(user: UserInfo) {
        val syncKey = user.googleId.ifBlank { user.email }
        if (syncKey.isBlank() || syncingGoogleUserId == syncKey || _uiState.value.isDeletingAccount) {
            return
        }

        syncingGoogleUserId = syncKey

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    syncUser(user)
                }
            }.onSuccess { result ->
                syncingGoogleUserId = null
                if (result.result == UserSyncResult.ACCESS_DENIED) {
                    blockedRestoreGoogleUserId = syncKey
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            authNotice = createNotice(result),
                            pendingAccessDeniedSignOut = true
                        )
                    }
                } else {
                    blockedRestoreGoogleUserId = null
                    val syncedUser = user.copy(
                        backendLanguageTag = result.languageTag
                    )
                    _uiState.update {
                        it.copy(
                            currentUser = syncedUser,
                            authNotice = createNotice(result),
                            pendingAccessDeniedSignOut = false
                        )
                    }
                }
            }.onFailure { error ->
                syncingGoogleUserId = null
                blockedRestoreGoogleUserId = syncKey
                _uiState.update {
                    it.copy(
                        currentUser = null,
                        authNotice = createNotice(AuthNoticeMessage.SERVER_ERROR),
                        pendingAccessDeniedSignOut = false
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

    fun onDeleteAccountRequested(user: UserInfo) {
        val deleteKey = user.googleId.ifBlank { user.email }
        if (
            deleteKey.isBlank() ||
            _uiState.value.isDeletingAccount ||
            _uiState.value.pendingDeleteAccountSignOut
        ) {
            return
        }

        _uiState.update { it.copy(isDeletingAccount = true) }

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    deleteUser(user.googleId)
                }
            }.onSuccess {
                blockedRestoreGoogleUserId = deleteKey
                syncingGoogleUserId = null
                _uiState.update {
                    it.copy(
                        authNotice = createNotice(AuthNoticeMessage.DELETE_ACCOUNT_SUCCESS),
                        isDeletingAccount = false,
                        pendingDeleteAccountSignOut = true,
                        pendingAccessDeniedSignOut = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        authNotice = createNotice(AuthNoticeMessage.DELETE_ACCOUNT_ERROR),
                        isDeletingAccount = false,
                        pendingDeleteAccountSignOut = false,
                        pendingAccessDeniedSignOut = false
                    )
                }
                Log.e(
                    "AUTH_VIEW_MODEL",
                    "No se pudo eliminar el usuario del backend",
                    error
                )
            }
        }
    }

    fun onUpdateUserProfile(updatedUser: UserInfo) {
        if (_uiState.value.currentUser?.googleId != updatedUser.googleId) {
            return
        }

        viewModelScope.launch {
            runCatching {
            }.onSuccess {
                _uiState.update { it.copy(currentUser = updatedUser) }
            }.onFailure { error ->
                Log.e(
                    "AUTH_VIEW_MODEL",
                    "No se pudo actualizar la personalización del usuario en el backend",
                    error
                )
            }
        }
    }

    private fun createNotice(result: UserSyncOutcome): AuthNotice {
        return when (result.result) {
            UserSyncResult.EXISTING_USER_LOGGED_IN -> {
                createNotice(AuthNoticeMessage.LOGIN_SUCCESS)
            }

            UserSyncResult.NEW_USER_CREATED -> {
                createNotice(AuthNoticeMessage.REGISTER_SUCCESS)
            }

            UserSyncResult.ACCESS_DENIED -> {
                createNotice(AuthNoticeMessage.ACCESS_DENIED)
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
