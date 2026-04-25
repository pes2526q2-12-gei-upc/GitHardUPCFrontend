package com.safesteps.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.eliminarUsuarioDelBackend
import com.safesteps.data.UserSyncOutcome
import com.safesteps.data.sincronizarUsuarioConBackend as sincronizarUsuarioConBackendApi
// import com.safesteps.data.sincronizarPersonalizacionUsuario as sincronizarPersonalizacionUsuarioApi
import com.safesteps.data.UserSyncResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AuthViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
                pendingDeleteAccountSignOut = false
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

    private fun sincronizarUsuarioConBackendAsync(user: UserInfo) {
        val syncKey = user.googleId.ifBlank { user.email }
        if (syncKey.isBlank() || syncingGoogleUserId == syncKey || _uiState.value.isDeletingAccount) {
            return
        }

        syncingGoogleUserId = syncKey

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    sincronizarUsuarioConBackendApi(user)
                }
            }.onSuccess { result ->
                syncingGoogleUserId = null
                blockedRestoreGoogleUserId = null
                val syncedUser = user.copy(
                    backendLanguageTag = result.languageTag
                    // routeColor = result.routeColor,
                    // nameStyle = result.nameStyle
                )
                _uiState.update {
                    it.copy(
                        currentUser = syncedUser,
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
                    eliminarUsuarioDelBackend(user.googleId)
                }
            }.onSuccess {
                blockedRestoreGoogleUserId = deleteKey
                syncingGoogleUserId = null
                _uiState.update {
                    it.copy(
                        authNotice = createNotice(AuthNoticeMessage.DELETE_ACCOUNT_SUCCESS),
                        isDeletingAccount = false,
                        pendingDeleteAccountSignOut = true
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        authNotice = createNotice(AuthNoticeMessage.DELETE_ACCOUNT_ERROR),
                        isDeletingAccount = false,
                        pendingDeleteAccountSignOut = false
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
                // withContext(ioDispatcher) {
                //     sincronizarPersonalizacionUsuarioApi(updatedUser)
                // }
            }.onSuccess {
                _uiState.update { it.copy(currentUser = updatedUser) }
            }.onFailure { error ->
                Log.e(
                    "AUTH_VIEW_MODEL",
                    "No se pudo actualizar la personalizaciÃ³n del usuario en el backend",
                    error
                )
            }
        }
    }

    private fun createNotice(result: UserSyncOutcome): AuthNotice {
        return when (result.result) {
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


