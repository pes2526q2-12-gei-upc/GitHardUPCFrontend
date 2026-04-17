package com.safesteps.i18n

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LanguageUiState(
    val currentLanguage: AppLanguage = AppLanguage.default,
    val canChangeLanguage: Boolean = false
)

class LanguageViewModel(
    private val repository: LanguagePreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    private var currentUserEmail: String? = null

    fun onUserChanged(email: String?) {
        if (email == currentUserEmail) {
            return
        }

        currentUserEmail = email
        val resolvedLanguage = repository.getLanguageForUser(email)

        _uiState.update {
            it.copy(
                currentLanguage = resolvedLanguage,
                canChangeLanguage = !email.isNullOrBlank()
            )
        }
    }

    fun onLanguageSelected(language: AppLanguage) {
        val email = currentUserEmail ?: return

        repository.saveLanguageForUser(email, language)
        _uiState.update { it.copy(currentLanguage = language) }
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
