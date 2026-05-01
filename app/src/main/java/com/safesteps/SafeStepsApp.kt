package com.safesteps

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
import com.safesteps.ui.notifications.ScreenNotificationManager

private enum class SafeStepsDestination {
    MAP,
    PROFILE,
    CUSTOMIZE
}

private data class AuthNoticeTexts(
    val title: String,
    val loginSuccess: String,
    val registerSuccess: String,
    val accountBanned: String,
    val accountSuspended: String,
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
    var issuesRefreshTrigger by rememberSaveable {
        mutableStateOf(0)
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

    BackHandler(
        enabled = isProfileDestination(currentDestination) ||
                currentDestination == SafeStepsDestination.CUSTOMIZE
    ) {
        when (currentDestination) {
            SafeStepsDestination.CUSTOMIZE -> {
                currentDestination = SafeStepsDestination.PROFILE
            }
            SafeStepsDestination.PROFILE -> {
                issuesRefreshTrigger += 1
                currentDestination = SafeStepsDestination.MAP
            }
            SafeStepsDestination.MAP -> Unit
        }
    }

    SafeStepsLocalizedContent(
        modifier = modifier,
        authUiState = authUiState,
        currentLanguage = languageUiState.currentLanguage,
        authViewModel = authViewModel,
        currentDestination = currentDestination,
        issuesRefreshTrigger = issuesRefreshTrigger,
        profileUiState = profileUiState,
        onLanguageSelected = onLanguageSelected,
        onFilterValueChange = profileViewModel::onFilterValueChanged,
        onFilterEnabledChange = profileViewModel::onFilterEnabledChanged,
        onLoginClick = onLoginClick,
        onNavigateToMap = {
            issuesRefreshTrigger += 1
            currentDestination = SafeStepsDestination.MAP
        },
        onNavigateToProfile = { currentDestination = SafeStepsDestination.PROFILE },
        onNavigateToCustomize = { currentDestination = SafeStepsDestination.CUSTOMIZE }
    )
}

@Composable
private fun HandleProfileRedirectEffect(
    currentUser: UserInfo?,
    currentDestination: SafeStepsDestination,
    onNavigateToMap: () -> Unit
) {
    LaunchedEffect(currentUser, currentDestination) {
        val needsLoggedUser = isProfileDestination(currentDestination) ||
                currentDestination == SafeStepsDestination.CUSTOMIZE
        if (currentUser == null && needsLoggedUser) {
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
    issuesRefreshTrigger: Int,
    profileUiState: com.safesteps.profile.ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit
) {
    ProvideLocalizedStrings(currentLanguage) {
        HandleAuthNoticeEffect(
            authUiState = authUiState,
            noticeTexts = AuthNoticeTexts(
                title = appString(R.string.notification_title_auth),
                loginSuccess = appString(R.string.auth_banner_login_success),
                registerSuccess = appString(R.string.auth_banner_register_success),
                accountBanned = appString(R.string.auth_banner_account_banned),
                accountSuspended = appString(R.string.auth_banner_account_suspended),
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
            issuesRefreshTrigger = issuesRefreshTrigger,
            profileUiState = profileUiState,
            onLanguageSelected = onLanguageSelected,
            onFilterValueChange = onFilterValueChange,
            onFilterEnabledChange = onFilterEnabledChange,
            onLoginClick = onLoginClick,
            onNavigateToMap = { onNavigateToMap() },
            onNavigateToProfile = { onNavigateToProfile() },
            onNavigateToCustomize = { onNavigateToCustomize() },
            onLogout = rememberLogoutToMapAction(
                authViewModel = authViewModel,
                onNavigateToMap = onNavigateToMap
            ),
            onDeleteAccount = authViewModel::onDeleteAccountRequested,
            onUpdateUserProfile = authViewModel::onUpdateUserProfile
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
    LaunchedEffect(
        authUiState.authNotice?.id,
        authUiState.pendingDeleteAccountSignOut,
        authUiState.pendingAccessDeniedSignOut
    ) {
        val notice = authUiState.authNotice ?: return@LaunchedEffect

        ScreenNotificationManager.showNotification(
            notificationName = noticeTexts.title,
            text = resolveAuthNoticeMessage(notice.message, noticeTexts)
        )

        if (authUiState.pendingDeleteAccountSignOut || authUiState.pendingAccessDeniedSignOut) {
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
    issuesRefreshTrigger: Int,
    profileUiState: com.safesteps.profile.ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (UserInfo) -> Unit,
    onUpdateUserProfile: (UserInfo) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val currentUser = authUiState.currentUser

        if (currentUser != null) {
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
                        issuesRefreshTrigger = issuesRefreshTrigger,
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
                issuesRefreshTrigger = issuesRefreshTrigger,
                onLoginClick = onLoginClick,
                onProfileClick = {}
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
        AuthNoticeMessage.ACCOUNT_BANNED -> noticeTexts.accountBanned
        AuthNoticeMessage.ACCOUNT_SUSPENDED -> noticeTexts.accountSuspended
        AuthNoticeMessage.SERVER_ERROR -> noticeTexts.serverError
        AuthNoticeMessage.DELETE_ACCOUNT_SUCCESS -> noticeTexts.deleteAccountSuccess
        AuthNoticeMessage.DELETE_ACCOUNT_ERROR -> noticeTexts.deleteAccountError
    }
}

private fun isProfileDestination(destination: SafeStepsDestination): Boolean {
    return destination == SafeStepsDestination.PROFILE
}