package com.safesteps.profile

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.IssueApiType
import com.safesteps.data.IssueRequestDTO
import com.safesteps.data.IssueResponseDTO
import com.safesteps.data.PhotonApi
import com.safesteps.data.actualitzarIncidencia
import com.safesteps.data.eliminarIncidencia
import com.safesteps.data.getIssuesByUser
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.LanguageSelector
import com.safesteps.i18n.appPlural
import com.safesteps.i18n.appString
import com.safesteps.map.IssueType
import com.safesteps.map.MapEventPreferences
import com.safesteps.map.ReportIssueDialog
import com.safesteps.notifications.SocketChannelPreference
import com.safesteps.notifications.SocketChannelPreferences
import com.safesteps.notifications.SocketChannelSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val filterLevelResIds = listOf(
    R.string.filter_level_low,
    R.string.filter_level_medium,
    R.string.filter_level_high,
    R.string.filter_level_required
)

internal val filterLevelDescriptionResIds = listOf(
    R.string.filter_level_low_description,
    R.string.filter_level_medium_description,
    R.string.filter_level_high_description,
    R.string.filter_level_required_description
)

data class ProfileGamificationState(
    val level: Long,
    val points: Long,
    val recompenses: Long,
    val isLoading: Boolean,
    val showLevelUpAnimation: Boolean,
    val showPrizeAnimation: Boolean,
    val lastOpenedPrize: com.safesteps.data.PremiResponse?
)

data class ProfileGamificationCallbacks(
    val onOpenPrize: () -> Unit,
    val onDismissLevelUp: () -> Unit,
    val onDismissPrize: () -> Unit
)

data class ProfileFilterState(
    val values: List<Int>,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val areEnabled: Boolean
)

class ProfileIssuesState(
    val userGoogleId: String,
    val scope: kotlinx.coroutines.CoroutineScope,
    val context: android.content.Context,
    val issueDeletedText: String,
    val issueUpdatedText: String,
    val issueActionErrorText: String
) {
    var userIssues by mutableStateOf<List<IssueResponseDTO>>(emptyList())
    var isLoadingIssues by mutableStateOf(true)
    var issueLoadFailed by mutableStateOf(false)
    var issueBeingEdited by mutableStateOf<IssueResponseDTO?>(null)
    var issueEditLocationText by mutableStateOf("")

    suspend fun loadUserIssues() {
        isLoadingIssues = true
        issueLoadFailed = false
        try {
            userIssues = getIssuesByUser(userGoogleId)
                .sortedByDescending { issue ->
                    issue.updatedAt.ifBlank { issue.createdAt }
                }
        } catch (_: Exception) {
            issueLoadFailed = true
        } finally {
            isLoadingIssues = false
        }
    }

    fun deleteIssue(issue: IssueResponseDTO) {
        scope.launch {
            try {
                eliminarIncidencia(issue.id)
                userIssues = userIssues.filter { it.id != issue.id }
                if (issueBeingEdited?.id == issue.id) {
                    issueBeingEdited = null
                }
                Toast.makeText(context, issueDeletedText, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, issueActionErrorText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateIssue(issue: IssueResponseDTO, type: IssueType, description: String?) {
        scope.launch {
            try {
                val updatedIssue = actualitzarIncidencia(
                    idIncidencia = issue.id,
                    request = IssueRequestDTO(
                        googleId = userGoogleId,
                        type = IssueApiType.valueOf(type.name),
                        description = description ?: "",
                        coordinates = issue.coordinates
                    )
                )

                userIssues = userIssues.map { currentIssue ->
                    if (currentIssue.id == updatedIssue.id) updatedIssue else currentIssue
                }
                issueBeingEdited = null
                Toast.makeText(context, issueUpdatedText, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, issueActionErrorText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun resolveLocationText(issue: IssueResponseDTO) {
        issueEditLocationText = "${issue.coordinates.lat}, ${issue.coordinates.lon}"
        try {
            val response = withContext(Dispatchers.IO) {
                PhotonApi.service.reverseGeocode(
                    lat = issue.coordinates.lat,
                    lon = issue.coordinates.lon
                )
            }
            val address = response.features.firstOrNull()?.properties?.getAddress()
            if (!address.isNullOrBlank()) {
                issueEditLocationText = address
            }
        } catch (_: Exception) {
            issueEditLocationText = "${issue.coordinates.lat}, ${issue.coordinates.lon}"
        }
    }
}

@Composable
fun rememberProfileIssuesState(
    userGoogleId: String,
    scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
    context: android.content.Context = LocalContext.current
): ProfileIssuesState {
    val issueDeletedText = appString(R.string.profile_issue_deleted_success)
    val issueUpdatedText = appString(R.string.profile_issue_updated_success)
    val issueActionErrorText = appString(R.string.profile_issue_action_error)

    val state = remember(userGoogleId) {
        ProfileIssuesState(
            userGoogleId = userGoogleId,
            scope = scope,
            context = context,
            issueDeletedText = issueDeletedText,
            issueUpdatedText = issueUpdatedText,
            issueActionErrorText = issueActionErrorText
        )
    }

    LaunchedEffect(userGoogleId) {
        state.loadUserIssues()
    }

    LaunchedEffect(state.issueBeingEdited?.id) {
        val issue = state.issueBeingEdited ?: return@LaunchedEffect
        state.resolveLocationText(issue)
    }

    return state
}

@Composable
private fun ProfileOverlays(
    gamification: ProfileGamificationState,
    gamificationCallbacks: ProfileGamificationCallbacks,
    issuesState: ProfileIssuesState
) {
    if (gamification.showLevelUpAnimation) {
        LevelUpAnimationOverlay(
            level = gamification.level,
            onDismiss = gamificationCallbacks.onDismissLevelUp
        )
    }

    if (gamification.showPrizeAnimation && gamification.lastOpenedPrize != null) {
        PrizeAnimationOverlay(
            prize = gamification.lastOpenedPrize,
            onDismiss = gamificationCallbacks.onDismissPrize
        )
    }

    issuesState.issueBeingEdited?.let { issue ->
        ReportIssueDialog(
            visible = true,
            isLoggedIn = true,
            defaultLocationText = issuesState.issueEditLocationText,
            onDismiss = { issuesState.issueBeingEdited = null },
            onConfirm = { type, _, description, _ ->
                issuesState.updateIssue(issue, type, description)
            },
            onNavigateToLogin = {},
            initialType = profileIssueType(issue.type),
            initialDescription = issue.description.orEmpty(),
            isEditMode = true,
            initialCoord = issue.coordinates
        )
    }
}

@Composable
fun ProfileScreen(
    user: UserInfo,
    gamification: ProfileGamificationState,
    gamificationCallbacks: ProfileGamificationCallbacks,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCustomizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isIssuesSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isNotificationSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var isMapSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteBanner by rememberSaveable { mutableStateOf(false) }

    val appContext = LocalContext.current.applicationContext
    val issuesState = rememberProfileIssuesState(userGoogleId = user.googleId)
    val socketChannelSettings by remember(appContext) {
        SocketChannelPreferences.settings(appContext)
    }.collectAsState()
    val mapEventSettings by remember(appContext) {
        MapEventPreferences.settings(appContext)
    }.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F7F5))
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ProfileTopBar(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))

            ProfileContentCard(
                user = user,
                gamification = gamification,
                gamificationCallbacks = gamificationCallbacks,
                isIssuesSectionExpanded = isIssuesSectionExpanded,
                onIssuesSectionExpandedChange = { isIssuesSectionExpanded = !isIssuesSectionExpanded },
                isNotificationSettingsExpanded = isNotificationSettingsExpanded,
                onNotificationSettingsExpandedChange = {
                    isNotificationSettingsExpanded = !isNotificationSettingsExpanded
                },
                isMapSettingsExpanded = isMapSettingsExpanded,
                onMapSettingsExpandedChange = {
                    isMapSettingsExpanded = !isMapSettingsExpanded
                },
                onCustomizeClick = onCustomizeClick,
                onLogout = onLogout,
                onDeleteAccount = onDeleteAccount,
                showDeleteBanner = showDeleteBanner,
                onShowDeleteBanner = { showDeleteBanner = true },
                onDismissDeleteBanner = { showDeleteBanner = false },
                issues = issuesState.userIssues,
                isLoadingIssues = issuesState.isLoadingIssues,
                issueLoadFailed = issuesState.issueLoadFailed,
                onRetryIssues = { issuesState.scope.launch { issuesState.loadUserIssues() } },
                onEditIssue = { issuesState.issueBeingEdited = it },
                onDeleteIssue = { issue -> issuesState.deleteIssue(issue) },
                socketChannelSettings = socketChannelSettings,
                onSocketChannelEnabledChange = { channel, enabled ->
                    SocketChannelPreferences.setChannelEnabled(
                        context = appContext,
                        channel = channel,
                        enabled = enabled
                    )
                },
                hideEventsDuringActiveRoute = mapEventSettings.hideEventsDuringActiveRoute,
                onHideEventsDuringActiveRouteChange = { enabled ->
                    MapEventPreferences.setHideEventsDuringActiveRoute(
                        context = appContext,
                        enabled = enabled
                    )
                },
                hideEventsDuringRoutePreview = mapEventSettings.hideEventsDuringRoutePreview,
                onHideEventsDuringRoutePreviewChange = { enabled ->
                    MapEventPreferences.setHideEventsDuringRoutePreview(
                        context = appContext,
                        enabled = enabled
                    )
                }
            )
        }

        ProfileOverlays(
            gamification = gamification,
            gamificationCallbacks = gamificationCallbacks,
            issuesState = issuesState
        )
    }
}


@Composable
private fun ProfileTopBar(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(60.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = appString(R.string.back),
                        tint = Color(0xFF33413B)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appString(R.string.profile_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier.width(60.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            LanguageSelector(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected
            )
        }
    }
}

@Composable
private fun ProfileContentCard(
    user: UserInfo,
    gamification: ProfileGamificationState,
    gamificationCallbacks: ProfileGamificationCallbacks,
    isIssuesSectionExpanded: Boolean,
    onIssuesSectionExpandedChange: () -> Unit,
    isNotificationSettingsExpanded: Boolean,
    onNotificationSettingsExpandedChange: () -> Unit,
    isMapSettingsExpanded: Boolean,
    onMapSettingsExpandedChange: () -> Unit,
    onCustomizeClick: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    showDeleteBanner: Boolean,
    onShowDeleteBanner: () -> Unit,
    onDismissDeleteBanner: () -> Unit,
    issues: List<IssueResponseDTO>,
    isLoadingIssues: Boolean,
    issueLoadFailed: Boolean,
    onRetryIssues: () -> Unit,
    onEditIssue: (IssueResponseDTO) -> Unit,
    onDeleteIssue: (IssueResponseDTO) -> Unit,
    socketChannelSettings: SocketChannelSettings,
    onSocketChannelEnabledChange: (SocketChannelPreference, Boolean) -> Unit,
    hideEventsDuringActiveRoute: Boolean,
    onHideEventsDuringActiveRouteChange: (Boolean) -> Unit,
    hideEventsDuringRoutePreview: Boolean,
    onHideEventsDuringRoutePreviewChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileHeaderSection(
                user = user,
                gamification = gamification,
                gamificationCallbacks = gamificationCallbacks
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = onCustomizeClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE5ECE7)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFF9FBFA),
                    contentColor = Color(0xFF23333A)
                )
            ) {
                Text(
                    text = appString(R.string.profile_customize),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            ProfileIssuesSection(
                issues = issues,
                isLoading = isLoadingIssues,
                loadFailed = issueLoadFailed,
                expanded = isIssuesSectionExpanded,
                onExpandedChange = onIssuesSectionExpandedChange,
                onRetry = onRetryIssues,
                onEdit = onEditIssue,
                onDelete = onDeleteIssue
            )

            Spacer(modifier = Modifier.height(20.dp))

            ProfileSocketSettingsSection(
                settings = socketChannelSettings,
                expanded = isNotificationSettingsExpanded,
                onExpandedChange = onNotificationSettingsExpandedChange,
                onChannelEnabledChange = onSocketChannelEnabledChange
            )

            Spacer(modifier = Modifier.height(20.dp))

            ProfileMapSettingsSection(
                expanded = isMapSettingsExpanded,
                onExpandedChange = onMapSettingsExpandedChange,
                hideEventsDuringActiveRoute = hideEventsDuringActiveRoute,
                onHideEventsDuringActiveRouteChange = onHideEventsDuringActiveRouteChange,
                hideEventsDuringRoutePreview = hideEventsDuringRoutePreview,
                onHideEventsDuringRoutePreviewChange = onHideEventsDuringRoutePreviewChange
            )

            Spacer(modifier = Modifier.height(28.dp))

            ProfileAccountActions(
                onLogout = onLogout,
                onDeleteAccount = onDeleteAccount,
                showDeleteBanner = showDeleteBanner,
                onShowDeleteBanner = onShowDeleteBanner,
                onDismissDeleteBanner = onDismissDeleteBanner
            )
        }
    }
}

@Composable
private fun ProfileSocketSettingsSection(
    settings: SocketChannelSettings,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onChannelEnabledChange: (SocketChannelPreference, Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, Color(0xFFE5ECE7), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF9FBFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandedChange)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appString(R.string.profile_socket_settings_title),
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = appString(R.string.profile_socket_settings_subtitle),
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C6A64)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                SocketChannelSettingRow(
                    title = appString(R.string.profile_socket_messages_title),
                    description = appString(R.string.profile_socket_messages_description),
                    enabled = settings.messagesEnabled,
                    onEnabledChange = { enabled ->
                        onChannelEnabledChange(SocketChannelPreference.MESSAGES, enabled)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SocketChannelSettingRow(
                    title = appString(R.string.profile_socket_emergency_title),
                    description = appString(R.string.profile_socket_emergency_description),
                    enabled = settings.emergencyEnabled,
                    onEnabledChange = { enabled ->
                        onChannelEnabledChange(SocketChannelPreference.EMERGENCY, enabled)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SocketChannelSettingRow(
                    title = appString(R.string.profile_socket_friend_requests_title),
                    description = appString(R.string.profile_socket_friend_requests_description),
                    enabled = settings.friendRequestsEnabled,
                    onEnabledChange = { enabled ->
                        onChannelEnabledChange(SocketChannelPreference.FRIEND_REQUESTS, enabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileMapSettingsSection(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    hideEventsDuringActiveRoute: Boolean,
    onHideEventsDuringActiveRouteChange: (Boolean) -> Unit,
    hideEventsDuringRoutePreview: Boolean,
    onHideEventsDuringRoutePreviewChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, Color(0xFFE5ECE7), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF9FBFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandedChange)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appString(R.string.profile_map_settings_title),
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = appString(R.string.profile_map_settings_subtitle),
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C6A64)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                SocketChannelSettingRow(
                    title = appString(R.string.profile_events_hide_started_title),
                    description = appString(R.string.profile_events_hide_started_description),
                    enabled = hideEventsDuringActiveRoute,
                    onEnabledChange = onHideEventsDuringActiveRouteChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                SocketChannelSettingRow(
                    title = appString(R.string.profile_events_hide_preview_title),
                    description = appString(R.string.profile_events_hide_preview_description),
                    enabled = hideEventsDuringRoutePreview,
                    onEnabledChange = onHideEventsDuringRoutePreviewChange
                )
            }
        }
    }
}

@Composable
private fun SocketChannelSettingRow(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                color = Color(0xFF77837D),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun ProfileHeaderSection(
    user: UserInfo,
    gamification: ProfileGamificationState,
    gamificationCallbacks: ProfileGamificationCallbacks
) {
    if (gamification.isLoading) {
        CircularProgressIndicator(color = Color(0xFF5E9F7A))
    } else {
        ProfileHeader(
            user = user,
            level = gamification.level,
            points = gamification.points
        )
    }

    if (gamification.recompenses > 0) {
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = gamificationCallbacks.onOpenPrize,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5E9F7A),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "🎁 Open reward (${gamification.recompenses} available)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    user: UserInfo,
    level: Long,
    points: Long
) {
    val currentLevel = level.toInt()
    val pointsForCurrentLevel = ((currentLevel - 1).toLong() * 10L).let { it * it }
    val pointsForNextLevel = (currentLevel.toLong() * 10L).let { it * it }
    val pointsInCurrentLevel = (points - pointsForCurrentLevel).coerceAtLeast(0)
    val pointsNeededForNext = pointsForNextLevel - pointsForCurrentLevel
    val progress = if (pointsNeededForNext > 0) {
        (pointsInCurrentLevel.toFloat() / pointsNeededForNext.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val tier = ((currentLevel - 1) / 5).coerceIn(0, 3)
    val tierColor = when (tier) {
        0 -> Color(0xFFB0BEC5)
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFF00E676)
        else -> Color(0xFFE040FB)
    }
    val borderStroke = BorderStroke(if (tier == 0) 2.dp else (tier + 2).dp, tierColor)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            border = borderStroke,
            modifier = Modifier.size(80.dp),
            color = Color.Transparent
        ) {
            if (!user.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = appString(R.string.profile_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(borderStroke.width)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(borderStroke.width),
                    shape = CircleShape,
                    color = Color(0xFF6DD29A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = user.email,
                color = Color(0xFF77837D),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appString(R.string.profile_level, currentLevel),
                    color = tierColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appString(R.string.profile_points, pointsInCurrentLevel.toInt(), pointsNeededForNext.toInt()),
                    color = Color(0xFF77837D),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = tierColor,
                trackColor = Color(0xFFE5ECE7)
            )
        }
    }
}

@Composable
private fun ProfileIssuesSection(
    issues: List<IssueResponseDTO>,
    isLoading: Boolean,
    loadFailed: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (IssueResponseDTO) -> Unit,
    onDelete: (IssueResponseDTO) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, Color(0xFFE5ECE7), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF9FBFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandedChange)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appString(R.string.profile_my_issues_title),
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = appString(R.string.profile_my_issues_subtitle),
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (!isLoading && !loadFailed && issues.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFC86A37).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = issues.size.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color(0xFFB76435),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C6A64)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFC86A37)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = appString(R.string.profile_my_issues_loading),
                                color = Color(0xFF5C6A64),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    loadFailed -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = appString(R.string.profile_my_issues_error),
                                color = Color(0xFF8F3D1B),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onRetry,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0B6A3)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF8F3D1B)
                                )
                            ) {
                                Text(
                                    text = appString(R.string.retry_action),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    issues.isEmpty() -> {
                        Text(
                            text = appString(R.string.profile_my_issues_empty),
                            color = Color(0xFF77837D),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {
                        issues.forEachIndexed { index, issue ->
                            ProfileIssueCard(
                                issue = issue,
                                onEdit = { onEdit(issue) },
                                onDelete = { onDelete(issue) }
                            )

                            if (index < issues.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileIssueCard(
    issue: IssueResponseDTO,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accepted = issue.status.equals("accepted", ignoreCase = true)
    val statusBackground = if (accepted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val statusContent = if (accepted) Color(0xFF2E7D32) else Color(0xFFEF6C00)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profileIssueTypeLabel(issue.type),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBackground
                ) {
                    Text(
                        text = if (accepted) {
                            appString(R.string.profile_issue_status_accepted)
                        } else {
                            appString(R.string.profile_issue_status_pending)
                        },
                        color = statusContent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = issue.description?.takeIf { it.isNotBlank() }
                    ?: appString(R.string.profile_issue_no_description),
                color = Color(0xFF4E5B56),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF4F7F5)
                ) {
                    Text(
                        text = "${appString(R.string.profile_issue_positive_votes)} ${issue.positiveVotes}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF4F7F5)
                ) {
                    Text(
                        text = "${appString(R.string.profile_issue_negative_votes)} ${issue.negativeVotes}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${appString(R.string.profile_issue_last_update)} ${profileIssueDate(issue)}",
                color = Color(0xFF77837D),
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFD9E2DD)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFDFEFE),
                        contentColor = Color(0xFF33413B)
                    )
                ) {
                    Text(
                        text = appString(R.string.edit_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD8563F),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = appString(R.string.delete_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAccountActions(
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    showDeleteBanner: Boolean,
    onShowDeleteBanner: () -> Unit,
    onDismissDeleteBanner: () -> Unit
) {
    Button(
        onClick = {
            onDismissDeleteBanner()
            onLogout()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC86A37),
            contentColor = Color.White
        )
    ) {
        Text(
            text = appString(R.string.log_out),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            onClick = onShowDeleteBanner,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD58A63)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color(0xFFB76435)
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = appString(R.string.delete_account_short),
                color = Color(0xFFB76435),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
        }
    }

    if (showDeleteBanner) {
        Spacer(modifier = Modifier.height(16.dp))

        DeleteAccountBanner(
            onDismiss = onDismissDeleteBanner,
            onConfirm = {
                onDismissDeleteBanner()
                onDeleteAccount()
            }
        )
    }
}

@Composable
private fun DeleteAccountBanner(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF0C9B8), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFF5F0)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = appString(R.string.delete_account_banner_title),
                color = Color(0xFF8F3D1B),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appString(R.string.delete_account_banner_message),
                color = Color(0xFF5C4034),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0B6A3)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFFFBF8),
                        contentColor = Color(0xFF8F3D1B)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = appString(R.string.cancel_action),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBF4F2D),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = appString(R.string.confirm_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun profileIssueTypeLabel(type: IssueApiType): String {
    return when (type) {
        IssueApiType.OBRES -> appString(R.string.issue_type_worksite)
        IssueApiType.ACCESSIBILITAT -> appString(R.string.issue_type_accessibility)
        IssueApiType.SEGURETAT -> appString(R.string.issue_type_security)
        IssueApiType.ALTRES -> appString(R.string.issue_type_others)
    }
}

private fun profileIssueType(type: IssueApiType): IssueType {
    return when (type) {
        IssueApiType.OBRES -> IssueType.OBRES
        IssueApiType.ACCESSIBILITAT -> IssueType.ACCESSIBILITAT
        IssueApiType.SEGURETAT -> IssueType.SEGURETAT
        IssueApiType.ALTRES -> IssueType.ALTRES
    }
}

private fun profileIssueDate(issue: IssueResponseDTO): String {
    return issue.updatedAt.takeIf { it.isNotBlank() }?.substringBefore("T")
        ?: issue.createdAt.substringBefore("T")
}
