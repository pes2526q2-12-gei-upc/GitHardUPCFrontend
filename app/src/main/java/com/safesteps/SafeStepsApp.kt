package com.safesteps

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.auth.AuthViewModel
import com.safesteps.auth.rememberGoogleSignOutAction
import com.safesteps.auth.rememberGoogleSignInAction
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
    val authUiState by authViewModel.uiState.collectAsState()
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

    BackHandler(enabled = currentDestination == SafeStepsDestination.PROFILE) {
        currentDestination = SafeStepsDestination.MAP
    }

    when {
        currentDestination == SafeStepsDestination.PROFILE && authUiState.currentUser != null -> {
            ProfileScreen(
                modifier = modifier,
                user = authUiState.currentUser!!,
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
