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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.auth.AuthNoticeMessage
import com.safesteps.auth.AuthUiState
import com.safesteps.auth.AuthViewModel
import com.safesteps.auth.UserInfo
import com.safesteps.auth.rememberGoogleSignOutAction
import com.safesteps.auth.rememberGoogleSignInAction
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.LanguagePreferencesRepository
import com.safesteps.i18n.LanguageViewModel
import com.safesteps.i18n.LanguageViewModelFactory
import com.safesteps.i18n.ProvideLocalizedStrings
import com.safesteps.i18n.appString
import com.safesteps.map.MapLibreScreen
import com.safesteps.profile.ProfileScreen
import com.safesteps.profile.ProfileViewModel

private enum class SafeStepsDestination {
    MAP,
    PROFILE,
    CUSTOMIZE // FEATURE RECUPERADA
}

private data class AuthNoticeTexts(
    val loginSuccess: String,
    val registerSuccess: String,
    val serverError: String,
    val deleteAccountSuccess: String,
    val deleteAccountError: String
)

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
    val profileViewModel: ProfileViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsState()
    val languageUiState by languageViewModel.uiState.collectAsState()
    val profileUiState by profileViewModel.uiState.collectAsState()
    var currentDestination by rememberSaveable {
        mutableStateOf(SafeStepsDestination.MAP)
    }
    val onLoginClick = rememberGoogleSignInAction(
        onUserLoggedIn = authViewModel::onUserLoggedIn,
        onSessionRestored = authViewModel::restoreLoggedUser
    )

    HandleProfileRedirectEffect(
        currentUser = authUiState.currentUser,
        currentDestination = currentDestination,
        onNavigateToMap = { currentDestination = SafeStepsDestination.MAP }
    )
    HandleLanguageSyncEffect(
        currentUser = authUiState.currentUser,
        onUserChanged = languageViewModel::onUserChanged
    )
    HandleProfileFiltersSyncEffect(
        currentUser = authUiState.currentUser,
        onUserChanged = profileViewModel::onCurrentUserChanged
    )
    HandleProfileFiltersRefreshEffect(
        currentUser = authUiState.currentUser,
        currentDestination = currentDestination,
        onProfileOpened = profileViewModel::onFiltersScreenOpened
    )
    val onLanguageSelected: (AppLanguage) -> Unit = remember(languageViewModel, authViewModel) {
        { language ->
            languageViewModel.onLanguageSelected(
                language = language,
                onLanguageUpdated = authViewModel::onCurrentUserLanguageChanged
            )
        }
    }

    // FEATURE RECUPERADA: BackHandler adaptado para volver del CUSTOMIZE al PROFILE
    BackHandler(enabled = isProfileDestination(currentDestination) || currentDestination == SafeStepsDestination.CUSTOMIZE) {
        if (currentDestination == SafeStepsDestination.CUSTOMIZE) {
            currentDestination = SafeStepsDestination.PROFILE
        } else {
            currentDestination = SafeStepsDestination.MAP
        }
    }

    SafeStepsLocalizedContent(
        modifier = modifier,
        authUiState = authUiState,
        currentLanguage = languageUiState.currentLanguage,
        authViewModel = authViewModel,
        currentDestination = currentDestination,
        profileUiState = profileUiState,
        onLanguageSelected = onLanguageSelected,
        onFilterValueChange = profileViewModel::onFilterValueChanged,
        onFilterEnabledChange = profileViewModel::onFilterEnabledChanged,
        onLoginClick = onLoginClick,
        onNavigateToMap = { currentDestination = SafeStepsDestination.MAP },
        onNavigateToProfile = { currentDestination = SafeStepsDestination.PROFILE },
        onNavigateToCustomize = { currentDestination = SafeStepsDestination.CUSTOMIZE } // FEATURE RECUPERADA
    )
}

@Composable
private fun HandleProfileRedirectEffect(
    currentUser: UserInfo?,
    currentDestination: SafeStepsDestination,
    onNavigateToMap: () -> Unit
) {
    // FEATURE RECUPERADA: Añadido CUSTOMIZE a las validaciones de redirección
    LaunchedEffect(currentUser, currentDestination) {
        if (currentUser == null && (isProfileDestination(currentDestination) || currentDestination == SafeStepsDestination.CUSTOMIZE)) {
            onNavigateToMap()
        }
    }
}

@Composable
private fun HandleLanguageSyncEffect(
    currentUser: UserInfo?,
    onUserChanged: (UserInfo?) -> Unit
) {
    LaunchedEffect(currentUser?.googleId, currentUser?.email, currentUser?.backendLanguageTag) {
        onUserChanged(currentUser)
    }
}

@Composable
private fun HandleProfileFiltersSyncEffect(
    currentUser: UserInfo?,
    onUserChanged: (UserInfo?) -> Unit
) {
    LaunchedEffect(currentUser?.googleId) {
        onUserChanged(currentUser)
    }
}

@Composable
private fun HandleProfileFiltersRefreshEffect(
    currentUser: UserInfo?,
    currentDestination: SafeStepsDestination,
    onProfileOpened: () -> Unit
) {
    LaunchedEffect(currentDestination, currentUser?.googleId) {
        if (isProfileDestination(currentDestination) && currentUser != null) {
            onProfileOpened()
        }
    }
}


@Composable
private fun SafeStepsLocalizedContent(
    modifier: Modifier,
    authUiState: AuthUiState,
    currentLanguage: AppLanguage,
    authViewModel: AuthViewModel,
    currentDestination: SafeStepsDestination,
    profileUiState: com.safesteps.profile.ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit // FEATURE RECUPERADA
) {
    ProvideLocalizedStrings(currentLanguage) {
        HandleAuthNoticeEffect(
            authUiState = authUiState,
            noticeTexts = AuthNoticeTexts(
                loginSuccess = appString(R.string.auth_banner_login_success),
                registerSuccess = appString(R.string.auth_banner_register_success),
                serverError = appString(R.string.auth_banner_server_error),
                deleteAccountSuccess = appString(R.string.delete_account_success),
                deleteAccountError = appString(R.string.delete_account_error)
            ),
            onLogout = rememberLogoutToMapAction(
                authViewModel = authViewModel,
                onNavigateToMap = onNavigateToMap
            ),
            onClearAuthNotice = authViewModel::clearAuthNotice
        )

        SafeStepsBody(
            modifier = modifier,
            authUiState = authUiState,
            currentLanguage = currentLanguage,
            currentDestination = currentDestination,
            profileUiState = profileUiState,
            onLanguageSelected = onLanguageSelected,
            onFilterValueChange = onFilterValueChange,
            onFilterEnabledChange = onFilterEnabledChange,
            onLoginClick = onLoginClick,
            onNavigateToMap = { onNavigateToMap() },
            onNavigateToProfile = { onNavigateToProfile() },
            onNavigateToCustomize = { onNavigateToCustomize() }, // FEATURE RECUPERADA
            onLogout = rememberLogoutToMapAction(
                authViewModel = authViewModel,
                onNavigateToMap = onNavigateToMap
            ),
            onDeleteAccount = authViewModel::onDeleteAccountRequested,
            onUpdateUserProfile = authViewModel::onUpdateUserProfile // FEATURE RECUPERADA
        )
    }
}

@Composable
private fun HandleAuthNoticeEffect(
    authUiState: AuthUiState,
    noticeTexts: AuthNoticeTexts,
    onLogout: () -> Unit,
    onClearAuthNotice: (Long) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(authUiState.authNotice?.id, authUiState.pendingDeleteAccountSignOut) {
        val notice = authUiState.authNotice ?: return@LaunchedEffect

        Toast.makeText(
            context,
            resolveAuthNoticeMessage(notice.message, noticeTexts),
            Toast.LENGTH_SHORT
        ).show()

        if (authUiState.pendingDeleteAccountSignOut) {
            onLogout()
        } else {
            onClearAuthNotice(notice.id)
        }
    }
}

@Composable
private fun SafeStepsBody(
    modifier: Modifier,
    authUiState: AuthUiState,
    currentLanguage: AppLanguage,
    currentDestination: SafeStepsDestination,
    profileUiState: com.safesteps.profile.ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit, // FEATURE RECUPERADA
    onLogout: () -> Unit,
    onDeleteAccount: (UserInfo) -> Unit,
    onUpdateUserProfile: (UserInfo) -> Unit // FEATURE RECUPERADA
) {
    Box(modifier = modifier.fillMaxSize()) {
        val currentUser = authUiState.currentUser

        if (currentUser != null) {
            // FEATURE RECUPERADA: Refactorizado usando "when" para manejar CUSTOMIZE
            when (currentDestination) {
                SafeStepsDestination.PROFILE -> {
                    ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        user = currentUser,
                        currentLanguage = currentLanguage,
                        onLanguageSelected = onLanguageSelected,
                        filterValues = profileUiState.filterValues,
                        onFilterValueChange = onFilterValueChange,
                        isLoadingFilters = profileUiState.isLoadingFilters,
                        areFiltersEnabled = !profileUiState.isLoadingFilters,
                        onBack = onNavigateToMap,
                        onLogout = onLogout,
                        onDeleteAccount = { onDeleteAccount(currentUser) },
                        onCustomizeClick = onNavigateToCustomize
                    )
                }
                SafeStepsDestination.CUSTOMIZE -> {
                    com.safesteps.profile.ProfileCustomizationScreen(
                        user = currentUser,
                        onBack = onNavigateToProfile,
                        onSave = onUpdateUserProfile,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SafeStepsDestination.MAP -> {
                    MapLibreScreen(
                        modifier = Modifier.fillMaxSize(),
                        currentUser = currentUser,
                        currentLanguage = currentLanguage,
                        onLoginClick = onLoginClick,
                        onProfileClick = onNavigateToProfile
                    )
                }
            }
        } else {
            MapLibreScreen(
                modifier = Modifier.fillMaxSize(),
                currentUser = currentUser,
                currentLanguage = currentLanguage,
                onLoginClick = onLoginClick,
                onProfileClick = {
                    if (currentUser != null) {
                        onNavigateToProfile()
                    }
                }
            )
        }
    }
}

@Composable
private fun rememberLogoutToMapAction(
    authViewModel: AuthViewModel,
    onNavigateToMap: () -> Unit
): () -> Unit {
    return rememberGoogleSignOutAction {
        authViewModel.onLogout()
        onNavigateToMap()
    }
}

private fun resolveAuthNoticeMessage(
    message: AuthNoticeMessage,
    noticeTexts: AuthNoticeTexts
): String {
    return when (message) {
        AuthNoticeMessage.LOGIN_SUCCESS -> noticeTexts.loginSuccess
        AuthNoticeMessage.REGISTER_SUCCESS -> noticeTexts.registerSuccess
        AuthNoticeMessage.SERVER_ERROR -> noticeTexts.serverError
        AuthNoticeMessage.DELETE_ACCOUNT_SUCCESS -> noticeTexts.deleteAccountSuccess
        AuthNoticeMessage.DELETE_ACCOUNT_ERROR -> noticeTexts.deleteAccountError
    }
}

private fun isProfileDestination(destination: SafeStepsDestination): Boolean {
    return destination == SafeStepsDestination.PROFILE
}