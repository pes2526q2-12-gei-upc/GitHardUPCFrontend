package com.safesteps

import android.util.Log
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
import com.safesteps.chat.ChatListScreen
import com.safesteps.chat.ChatListViewModel
import com.safesteps.chat.ConversationScreen
import com.safesteps.chat.CreateChatScreen
import com.safesteps.data.RouteCompletionResponse
import com.safesteps.profile.FriendListItemUiState
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.LanguagePreferencesRepository
import com.safesteps.i18n.LanguageViewModel
import com.safesteps.i18n.LanguageViewModelFactory
import com.safesteps.i18n.ProvideLocalizedStrings
import com.safesteps.i18n.appString
import com.safesteps.map.CommunityMenuScreen
import com.safesteps.map.MapLibreScreen
import com.safesteps.notifications.BackendWebSocketManager
import com.safesteps.notifications.persistEmergencyNotificationLanguage
import com.safesteps.notifications.persistEmergencyNotificationUser
import com.safesteps.notifications.syncCurrentFcmTokenForUser
import com.safesteps.profile.ProfileFilterState
import com.safesteps.profile.ProfileGamificationCallbacks
import com.safesteps.profile.ProfileGamificationState
import com.safesteps.profile.FriendSearchScreen
import com.safesteps.profile.FriendsScreen
import com.safesteps.profile.ProfileScreen
import com.safesteps.profile.ProfileUiState
import com.safesteps.profile.ProfileViewModel
import com.safesteps.profile.RouteFiltersScreen
import com.safesteps.ui.notifications.ScreenNotificationManager

private enum class SafeStepsDestination {
    MAP,
    MENU,
    FRIENDS,
    ROUTE_FILTERS,
    PROFILE,
    CUSTOMIZE,
    FRIEND_SEARCH,
    CHAT,
    CHAT_CONVERSATION,
    CREATE_CHAT
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
    val languageViewModel: LanguageViewModel = viewModel(factory = LanguageViewModelFactory(languageRepository))
    val profileViewModel: ProfileViewModel = viewModel()
    val chatListViewModel: ChatListViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsState()
    val languageUiState by languageViewModel.uiState.collectAsState()
    val profileUiState by profileViewModel.uiState.collectAsState()
    var currentDestination by rememberSaveable {
        mutableStateOf(SafeStepsDestination.MAP)
    }
    var profileBackDestination by rememberSaveable {
        mutableStateOf(SafeStepsDestination.MAP)
    }

    var chatBackDestination by rememberSaveable { mutableStateOf(SafeStepsDestination.FRIENDS) }
    var currentChatId by rememberSaveable { mutableStateOf(-1L) }
    var currentChatOtherName by rememberSaveable { mutableStateOf("") }
    var conversationBackToList by rememberSaveable { mutableStateOf(false) }

    var issuesRefreshTrigger by rememberSaveable {
        mutableStateOf(0)
    }

    val onLoginClick = rememberGoogleSignInAction(
        onUserLoggedIn = authViewModel::onUserLoggedIn,
        onSessionRestored = authViewModel::restoreLoggedUser
    )
    val onNavigateToMap = {
        profileBackDestination = SafeStepsDestination.MAP
        issuesRefreshTrigger += 1
        currentDestination = SafeStepsDestination.MAP
    }
    val onNavigateToMenu = {
        currentDestination = SafeStepsDestination.MENU
    }
    val onNavigateToFriends = {
        currentDestination = SafeStepsDestination.FRIENDS
    }
    val onNavigateToRouteFilters = {
        currentDestination = SafeStepsDestination.ROUTE_FILTERS
    }
    val onNavigateToProfileFromMap = {
        profileBackDestination = SafeStepsDestination.MAP
        currentDestination = SafeStepsDestination.PROFILE
    }
    val onNavigateToProfileFromMenu = {
        profileBackDestination = SafeStepsDestination.MENU
        currentDestination = SafeStepsDestination.PROFILE
    }
    val onNavigateToProfileFromFriendSearch = {
        profileBackDestination = SafeStepsDestination.FRIEND_SEARCH
        currentDestination = SafeStepsDestination.PROFILE
    }

    val onNavigatToChatFromFriends: (FriendListItemUiState) -> Unit = { friend ->
        val user = authUiState.currentUser
        if (user != null) {
            chatListViewModel.onCurrentUserChanged(user.googleId, user.username)
            chatListViewModel.findOrCreatePrivateChat(friend.googleId, friend.username) { chatId ->
                currentChatId = chatId
                currentChatOtherName = friend.username
                conversationBackToList = false
                chatBackDestination = SafeStepsDestination.FRIENDS
                currentDestination = SafeStepsDestination.CHAT_CONVERSATION
            }
        }
    }

    val onNavigateFromListToConversation: (Long, String) -> Unit = { chatId, name ->
        currentChatId = chatId
        currentChatOtherName = name
        conversationBackToList = true
        currentDestination = SafeStepsDestination.CHAT_CONVERSATION
    }

    val onNavigateToCreateChat = {
        authUiState.currentUser?.let {
            chatListViewModel.onCurrentUserChanged(it.googleId, it.username)
        }
        currentDestination = SafeStepsDestination.CREATE_CHAT
    }

    val onNavigateBackFromCreateChat = {
        currentDestination = SafeStepsDestination.CHAT
    }

    val onChatCreatedNavigate: (Long, String) -> Unit = { chatId, chatName ->
        currentChatId = chatId
        currentChatOtherName = chatName
        conversationBackToList = true
        currentDestination = SafeStepsDestination.CHAT_CONVERSATION
    }

    val onNavigateBackFromConversation = {
        if (conversationBackToList) {
            currentDestination = SafeStepsDestination.CHAT
        } else {
            currentDestination = SafeStepsDestination.FRIENDS
        }
    }

    val onNavigateToChatFromMenu = {
        chatBackDestination = SafeStepsDestination.MENU
        currentDestination = SafeStepsDestination.CHAT
    }

    val onNavigateBackFromChat = {
        when(chatBackDestination){
            SafeStepsDestination.FRIENDS -> {
                currentDestination = SafeStepsDestination.FRIENDS
            }
            SafeStepsDestination.MENU -> {
                currentDestination = SafeStepsDestination.MENU
            }
            else -> {
                chatBackDestination = SafeStepsDestination.MAP
                currentDestination = SafeStepsDestination.MAP
            }
        }
    }

    val onNavigateBackFromProfile = {
        when (profileBackDestination) {
            SafeStepsDestination.MENU -> {
                currentDestination = SafeStepsDestination.MENU
            }
            SafeStepsDestination.FRIENDS -> {
                currentDestination = SafeStepsDestination.FRIENDS
            }
            SafeStepsDestination.FRIEND_SEARCH -> {
                currentDestination = SafeStepsDestination.FRIEND_SEARCH
            }
            else -> {
                profileBackDestination = SafeStepsDestination.MAP
                issuesRefreshTrigger += 1
                currentDestination = SafeStepsDestination.MAP
            }
        }
    }


    HandleProfileRedirectEffect(
        currentUser = authUiState.currentUser,
        currentDestination = currentDestination,
        onNavigateToMap = onNavigateToMap
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
    HandleProfileDataRefreshEffect(
        currentUser = authUiState.currentUser,
        currentDestination = currentDestination,
        onProfileOpened = profileViewModel::onProfileScreenOpened
    )
    HandleBackendWebSocketEffect(currentUser = authUiState.currentUser)

    HandleNotificationRegistrationEffect(
        currentUser = authUiState.currentUser,
        currentLanguage = languageUiState.currentLanguage
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
        enabled = currentDestination != SafeStepsDestination.MAP
    ) {
        when (currentDestination) {
            SafeStepsDestination.CUSTOMIZE -> {
                currentDestination = SafeStepsDestination.PROFILE
            }
            SafeStepsDestination.MENU -> {
                currentDestination = SafeStepsDestination.MAP
            }
            SafeStepsDestination.FRIENDS -> {
                currentDestination = SafeStepsDestination.MENU
            }
            SafeStepsDestination.ROUTE_FILTERS -> {
                currentDestination = SafeStepsDestination.MENU
            }
            SafeStepsDestination.FRIEND_SEARCH -> {
                currentDestination = SafeStepsDestination.FRIENDS
            }
            SafeStepsDestination.PROFILE -> {
                onNavigateBackFromProfile()
            }
            SafeStepsDestination.CHAT -> {
                onNavigateBackFromChat()
            }
            SafeStepsDestination.CHAT_CONVERSATION -> {
                onNavigateBackFromConversation()
            }
            SafeStepsDestination.CREATE_CHAT -> {
                currentDestination = SafeStepsDestination.CHAT
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
        onNavigateToMap = onNavigateToMap,
        onNavigateToMenu = onNavigateToMenu,
        onNavigateToFriends = onNavigateToFriends,
        onNavigateToRouteFilters = onNavigateToRouteFilters,
        onNavigateBackFromProfile = onNavigateBackFromProfile,
        onNavigateToProfileFromMap = onNavigateToProfileFromMap,
        onNavigateToProfileFromMenu = onNavigateToProfileFromMenu,
        onNavigateToProfileFromFriendSearch = onNavigateToProfileFromFriendSearch,
        onNavigateBackFromChat = onNavigateBackFromChat,
        onNavigateToFriendsFromChat = onNavigatToChatFromFriends,
        onNavigateToMenuFromChat = onNavigateToChatFromMenu,
        onNavigateFromListToConversation = onNavigateFromListToConversation,
        onNavigateBackFromConversation = onNavigateBackFromConversation,
        onNavigateToCreateChat = onNavigateToCreateChat,
        onNavigateBackFromCreateChat = onNavigateBackFromCreateChat,
        onChatCreatedNavigate = onChatCreatedNavigate,
        currentChatId = currentChatId,
        currentChatOtherName = currentChatOtherName,
        onReturnToProfile = { currentDestination = SafeStepsDestination.PROFILE },
        onNavigateToCustomize = { currentDestination = SafeStepsDestination.CUSTOMIZE },
        onNavigateToFriendSearch = { currentDestination = SafeStepsDestination.FRIEND_SEARCH },
        onRouteCompleted = { response -> profileViewModel.onRouteCompleted(response) },
        onOpenPrize = profileViewModel::openPrize,
        onDismissLevelUp = profileViewModel::dismissLevelUpAnimation,
        onDismissPrize = profileViewModel::dismissPrizeAnimation
    )
}

@Composable
private fun HandleProfileDataRefreshEffect(
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
private fun HandleProfileRedirectEffect(
    currentUser: UserInfo?,
    currentDestination: SafeStepsDestination,
    onNavigateToMap: () -> Unit
) {
    LaunchedEffect(currentUser, currentDestination) {
        val needsLoggedUser = currentDestination != SafeStepsDestination.MAP
        if (currentUser == null && needsLoggedUser) {
            onNavigateToMap()
        }
    }
}

@Composable
private fun HandleBackendWebSocketEffect(currentUser: UserInfo?) {
    val appContext = LocalContext.current.applicationContext
    val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() }

    LaunchedEffect(appContext, googleId) {
        if (googleId == null) {
            BackendWebSocketManager.disconnect()
        } else {
            BackendWebSocketManager.connect(appContext, googleId)
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            BackendWebSocketManager.disconnect()
        }
    }
}

@Composable
private fun HandleNotificationRegistrationEffect(
    currentUser: UserInfo?,
    currentLanguage: AppLanguage
) {
    val appContext = LocalContext.current.applicationContext
    val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() }

    LaunchedEffect(appContext, googleId, currentLanguage) {
        persistEmergencyNotificationLanguage(appContext, currentLanguage.languageTag)
        persistEmergencyNotificationUser(appContext, googleId)
        if (googleId != null) {
            runCatching {
                syncCurrentFcmTokenForUser(appContext, googleId)
            }.onFailure { error ->
                Log.w(
                    "EMERGENCY_NOTIFICATIONS",
                    "No se pudo sincronizar el token FCM del usuario actual",
                    error
                )
            }
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
        if (currentDestination == SafeStepsDestination.ROUTE_FILTERS && currentUser != null) {
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
    profileUiState: ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToRouteFilters: () -> Unit,
    onNavigateBackFromProfile: () -> Unit,
    onNavigateToProfileFromMap: () -> Unit,
    onNavigateToProfileFromMenu: () -> Unit,
    onNavigateToProfileFromFriendSearch: () -> Unit,
    onNavigateBackFromChat: () -> Unit,
    onNavigateToFriendsFromChat: (FriendListItemUiState) -> Unit,
    onNavigateToMenuFromChat: () -> Unit,
    onNavigateFromListToConversation: (Long, String) -> Unit,
    onNavigateBackFromConversation: () -> Unit,
    onNavigateToCreateChat: () -> Unit,
    onNavigateBackFromCreateChat: () -> Unit,
    onChatCreatedNavigate: (Long, String) -> Unit,
    currentChatId: Long,
    currentChatOtherName: String,
    onReturnToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit,
    onNavigateToFriendSearch: () -> Unit,
    onRouteCompleted: (RouteCompletionResponse) -> Unit,
    onOpenPrize: () -> Unit,
    onDismissLevelUp: () -> Unit,
    onDismissPrize: () -> Unit
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
            onNavigateToMap = onNavigateToMap,
            onNavigateToMenu = onNavigateToMenu,
            onNavigateToFriends = onNavigateToFriends,
            onNavigateToRouteFilters = onNavigateToRouteFilters,
            onNavigateBackFromProfile = onNavigateBackFromProfile,
            onNavigateToProfileFromMap = onNavigateToProfileFromMap,
            onNavigateToProfileFromMenu = onNavigateToProfileFromMenu,
            onNavigateToProfileFromFriendSearch = onNavigateToProfileFromFriendSearch,
            onNavigateBackFromChat = onNavigateBackFromChat,
            onNavigateToChatFromFriends = onNavigateToFriendsFromChat,
            onNavigateToChatFromMenu = onNavigateToMenuFromChat,
            onNavigateFromListToConversation = onNavigateFromListToConversation,
            onNavigateBackFromConversation = onNavigateBackFromConversation,
            onNavigateToCreateChat = onNavigateToCreateChat,
            onNavigateBackFromCreateChat = onNavigateBackFromCreateChat,
            onChatCreatedNavigate = onChatCreatedNavigate,
            currentChatId = currentChatId,
            currentChatOtherName = currentChatOtherName,
            onReturnToProfile = onReturnToProfile,
            onNavigateToCustomize = onNavigateToCustomize,
            onNavigateToFriendSearch = onNavigateToFriendSearch,
            onLogout = rememberLogoutToMapAction(
                authViewModel = authViewModel,
                onNavigateToMap = onNavigateToMap
            ),
            onDeleteAccount = authViewModel::onDeleteAccountRequested,
            onUpdateUserProfile = authViewModel::onUpdateUserProfile,
            onRouteCompleted = onRouteCompleted,
            onOpenPrize = onOpenPrize,
            onDismissLevelUp = onDismissLevelUp,
            onDismissPrize = onDismissPrize
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
    profileUiState: ProfileUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit,
    onFilterEnabledChange: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToRouteFilters: () -> Unit,
    onNavigateBackFromProfile: () -> Unit,
    onNavigateToProfileFromMap: () -> Unit,
    onNavigateToProfileFromMenu: () -> Unit,
    onNavigateToProfileFromFriendSearch: () -> Unit,
    onNavigateBackFromChat: () -> Unit,
    onNavigateToChatFromFriends: (FriendListItemUiState) -> Unit,
    onNavigateToChatFromMenu: () -> Unit,
    onNavigateFromListToConversation: (Long, String) -> Unit,
    onNavigateBackFromConversation: () -> Unit,
    onNavigateToCreateChat: () -> Unit,
    onNavigateBackFromCreateChat: () -> Unit,
    onChatCreatedNavigate: (Long, String) -> Unit,
    currentChatId: Long,
    currentChatOtherName: String,
    onReturnToProfile: () -> Unit,
    onNavigateToCustomize: () -> Unit,
    onNavigateToFriendSearch: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (UserInfo) -> Unit,
    onUpdateUserProfile: (UserInfo) -> Unit,
    onRouteCompleted: (RouteCompletionResponse) -> Unit,
    onOpenPrize: () -> Unit,
    onDismissLevelUp: () -> Unit,
    onDismissPrize: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val currentUser = authUiState.currentUser

        if (currentUser != null) {
            when (currentDestination) {
                SafeStepsDestination.PROFILE -> {
                    ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        user = currentUser,
                        gamification = ProfileGamificationState(
                            level = profileUiState.level,
                            points = profileUiState.points,
                            recompenses = profileUiState.recompenses,
                            isLoading = profileUiState.isLoadingProfile,
                            showLevelUpAnimation = profileUiState.showLevelUpAnimation,
                            showPrizeAnimation = profileUiState.showPrizeAnimation,
                            lastOpenedPrize = profileUiState.lastOpenedPrize
                        ),
                        gamificationCallbacks = ProfileGamificationCallbacks(
                            onOpenPrize = onOpenPrize,
                            onDismissLevelUp = onDismissLevelUp,
                            onDismissPrize = onDismissPrize
                        ),
                        currentLanguage = currentLanguage,
                        onLanguageSelected = onLanguageSelected,
                        onBack = onNavigateBackFromProfile,
                        onLogout = onLogout,
                        onDeleteAccount = { onDeleteAccount(currentUser) },
                        onCustomizeClick = onNavigateToCustomize
                    )
                }

                SafeStepsDestination.ROUTE_FILTERS -> {
                    RouteFiltersScreen(
                        filterState = ProfileFilterState(
                            values = profileUiState.filterValues,
                            isLoading = profileUiState.isLoadingFilters,
                            isSaving = profileUiState.isSavingFilters,
                            areEnabled = !profileUiState.isLoadingFilters
                        ),
                        onFilterValueChange = onFilterValueChange,
                        onBack = onNavigateToMenu,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.CUSTOMIZE -> {
                    com.safesteps.profile.ProfileCustomizationScreen(
                        user = currentUser,
                        onBack = onReturnToProfile,
                        unlockedPremis = profileUiState.premis,
                        onSave = onUpdateUserProfile,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.MENU -> {
                    CommunityMenuScreen(
                        user = currentUser,
                        onBack = onNavigateToMap,
                        onProfileClick = onNavigateToProfileFromMenu,
                        onFriendsClick = onNavigateToFriends,
                        onChatClick = onNavigateToChatFromMenu,
                        onRouteFiltersClick = onNavigateToRouteFilters,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.FRIENDS -> {
                    FriendsScreen(
                        user = currentUser,
                        onBack = onNavigateToMenu,
                        onAddFriendClick = onNavigateToFriendSearch,
                        onChatClick = onNavigateToChatFromFriends,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.FRIEND_SEARCH -> {
                    FriendSearchScreen(
                        user = currentUser,
                        onBack = onNavigateToFriends,
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
                        onMenuClick = onNavigateToMenu,
                        onProfileClick = onNavigateToProfileFromMap,
                        onRouteCompleted = onRouteCompleted
                    )
                }

                SafeStepsDestination.CHAT -> {
                    ChatListScreen(
                        user = currentUser,
                        onBack = onNavigateBackFromChat,
                        onChatSelected = onNavigateFromListToConversation,
                        onCreateChat = onNavigateToCreateChat,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.CHAT_CONVERSATION -> {
                    ConversationScreen(
                        chatId = currentChatId,
                        otherParticipantName = currentChatOtherName,
                        user = currentUser,
                        onBack = onNavigateBackFromConversation,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SafeStepsDestination.CREATE_CHAT -> {
                    CreateChatScreen(
                        user = currentUser,
                        onBack = onNavigateBackFromCreateChat,
                        onChatCreated = onChatCreatedNavigate,
                        modifier = Modifier.fillMaxSize()
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
                onMenuClick = {},
                onProfileClick = {},
                onRouteCompleted = onRouteCompleted
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
