package com.safesteps

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.auth.AuthViewModel
import com.safesteps.auth.rememberGoogleSignOutAction
import com.safesteps.auth.rememberGoogleSignInAction
import com.safesteps.i18n.LanguagePreferencesRepository
import com.safesteps.i18n.LanguageViewModel
import com.safesteps.i18n.LanguageViewModelFactory
import com.safesteps.i18n.ProvideLocalizedStrings
import com.safesteps.map.MapLibreScreen
import com.safesteps.profile.ProfileScreen

private enum class SafeStepsDestination {
    MAP,
    PROFILE
}

@Composable
fun SafeStepsApp(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current.applicationContext
    val languageRepository = remember(context) { LanguagePreferencesRepository(context) }
    val languageViewModel: LanguageViewModel = viewModel(
        factory = LanguageViewModelFactory(languageRepository)
    )
    val authUiState by authViewModel.uiState.collectAsState()
    val languageUiState by languageViewModel.uiState.collectAsState()
    var currentDestination by rememberSaveable {
        mutableStateOf(SafeStepsDestination.MAP)
    }
    val onLoginClick = rememberGoogleSignInAction(
        onUserLoggedIn = authViewModel::onUserLoggedIn,
        onSessionRestored = authViewModel::restoreLoggedUser
    )
    val onLogoutClick = rememberGoogleSignOutAction {
        authViewModel.onLogout()
    }

    LaunchedEffect(authUiState.currentUser, currentDestination) {
        if (authUiState.currentUser == null && currentDestination == SafeStepsDestination.PROFILE) {
            currentDestination = SafeStepsDestination.MAP
        }
    }

    LaunchedEffect(authUiState.currentUser?.email) {
        languageViewModel.onUserChanged(authUiState.currentUser?.email)
    }

    BackHandler(enabled = currentDestination == SafeStepsDestination.PROFILE) {
        currentDestination = SafeStepsDestination.MAP
    }

    ProvideLocalizedStrings(languageUiState.currentLanguage) {
        when {
            currentDestination == SafeStepsDestination.PROFILE && authUiState.currentUser != null -> {
                ProfileScreen(
                    modifier = modifier,
                    user = authUiState.currentUser!!,
                    currentLanguage = languageUiState.currentLanguage,
                    onLanguageSelected = languageViewModel::onLanguageSelected,
                    onBack = { currentDestination = SafeStepsDestination.MAP },
                    onLogout = {
                        onLogoutClick()
                        currentDestination = SafeStepsDestination.MAP
                    }
                )
            }

            else -> {
                MapLibreScreen(
                    modifier = modifier,
                    currentUser = authUiState.currentUser,
                    currentLanguage = languageUiState.currentLanguage,
                    onLoginClick = onLoginClick,
                    onProfileClick = {
                        if (authUiState.currentUser != null) {
                            currentDestination = SafeStepsDestination.PROFILE
                        }
                    }
                )
            }
        }
    }
}
