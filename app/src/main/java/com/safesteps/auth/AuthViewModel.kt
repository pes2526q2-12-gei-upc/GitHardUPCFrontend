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

    fun loginWithEmail(username: String, password: String) {
        // TODO: Aquí hauràs de fer la crida a la teva API/Backend o Firebase per validar l'usuari.
        // Simulació per ara: creem un UserInfo amb les dades per iniciar sessió automàticament al test.
        val user = UserInfo(
            username = username,
            email = "", // Hauries de recuperar l'email des del teu backend
            photoUrl = null
        )
        onUserLoggedIn(user)
    }

    fun registerWithEmail(username: String, email: String, password: String) {
        // TODO: Aquí hauràs de fer la crida a la teva API/Backend per crear el nou compte.
        // Simulació per ara: iniciem la sessió directament després del registre.
        val user = UserInfo(
            username = username,
            email = email,
            photoUrl = null
        )
        onUserLoggedIn(user)
    }
}
