package com.safesteps

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.safesteps.R
import com.safesteps.auth.AuthNoticeMessage
import com.safesteps.auth.AuthViewModel
import com.safesteps.auth.rememberGoogleSignOutAction
import com.safesteps.auth.rememberGoogleSignInAction
import com.safesteps.i18n.LanguagePreferencesRepository
import com.safesteps.i18n.LanguageViewModel
import com.safesteps.i18n.LanguageViewModelFactory
import com.safesteps.i18n.ProvideLocalizedStrings
import com.safesteps.i18n.appString
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
    val appContext = LocalContext.current.applicationContext
    val languageRepository = remember(appContext) { LanguagePreferencesRepository(appContext) }
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
        val context = LocalContext.current
        val loginSuccessText = appString(R.string.auth_banner_login_success)
        val registerSuccessText = appString(R.string.auth_banner_register_success)
        val serverErrorText = appString(R.string.auth_banner_server_error)

        LaunchedEffect(authUiState.authNotice?.id) {
            val notice = authUiState.authNotice ?: return@LaunchedEffect
            Toast.makeText(
                context,
                when (notice.message) {
                    AuthNoticeMessage.LOGIN_SUCCESS -> loginSuccessText
                    AuthNoticeMessage.REGISTER_SUCCESS -> registerSuccessText
                    AuthNoticeMessage.SERVER_ERROR -> serverErrorText
                },
                Toast.LENGTH_SHORT
            ).show()
            authViewModel.clearAuthNotice(notice.id)
        }

        Box(modifier = modifier.fillMaxSize()) {
            when {
                currentDestination == SafeStepsDestination.PROFILE && authUiState.currentUser != null -> {
                    ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.fillMaxSize(),
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
}
