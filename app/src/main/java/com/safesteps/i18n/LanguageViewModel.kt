package com.safesteps.i18n

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.updateLenguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LanguageUiState(
    val currentLanguage: AppLanguage = AppLanguage.default,
    val canChangeLanguage: Boolean = false
)

class LanguageViewModel(
    private val repository: LanguagePreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var currentBackendLanguageTag: String? = null

    fun onUserChanged(user: UserInfo?) {
        val googleId = user?.googleId
        val email = user?.email
        val backendLanguageTag = user?.backendLanguageTag

        if (
            googleId == currentUser?.googleId &&
            email == currentUser?.email &&
            backendLanguageTag == currentBackendLanguageTag
        ) {
            return
        }

        currentUser = user
        currentBackendLanguageTag = backendLanguageTag

        val resolvedLanguage = when {
            !email.isNullOrBlank() && !backendLanguageTag.isNullOrBlank() -> {
                val backendLanguage = AppLanguage.fromLanguageTag(backendLanguageTag)
                repository.saveLanguageForUser(email, backendLanguage)
                backendLanguage
            }

            else -> repository.getLanguageForUser(email)
        }

        _uiState.update {
            it.copy(
                currentLanguage = resolvedLanguage,
                canChangeLanguage = !email.isNullOrBlank()
            )
        }
    }

    fun onLanguageSelected(
        language: AppLanguage,
        onLanguageUpdated: (String, String) -> Unit = { _, _ -> }
    ) {
        val user = currentUser ?: return
        val email = user.email

        repository.saveLanguageForUser(email, language)
        currentBackendLanguageTag = language.languageTag
        _uiState.update { it.copy(currentLanguage = language) }
        if (user.googleId.isNotBlank()) {
            onLanguageUpdated(user.googleId, language.languageTag)
        }

        if (user.googleId.isBlank()) {
            return
        }

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    updateLenguage(user, language)
                }
            }.onSuccess { updatedLanguageTag ->
                val resolvedLanguage = AppLanguage.fromLanguageTag(updatedLanguageTag)
                repository.saveLanguageForUser(email, resolvedLanguage)
                currentBackendLanguageTag = resolvedLanguage.languageTag
                _uiState.update { it.copy(currentLanguage = resolvedLanguage) }
                onLanguageUpdated(user.googleId, resolvedLanguage.languageTag)
            }.onFailure { error ->
                Log.e(
                    "LANGUAGE_VIEW_MODEL",
                    "No se pudo sincronizar el idioma del usuario con el backend",
                    error
                )
            }
        }
    }
}

class LanguageViewModelFactory(
    private val repository: LanguagePreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LanguageViewModel::class.java)) {
            return LanguageViewModel(repository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
