package com.safesteps.map

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.auth.UserInfo
import com.safesteps.domain.RoutePriority
import com.safesteps.R
import com.safesteps.i18n.appString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ShareRouteScreen(
    user: UserInfo,
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    originAddress: String?,
    destAddress: String?,
    distanceText: String,
    durationText: String,
    routePriority: RoutePriority?,
    onBack: () -> Unit,
    onShared: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareRouteViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(user.googleId) {
        viewModel.onCurrentUserChanged(user)
        viewModel.onScreenOpened()
    }

    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            viewModel.dismissSendSuccess()
            onShared()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ShareRouteTopBar(onBack = onBack)

            ShareRouteTabs(
                activeTab = uiState.activeTab,
                onTabSelected = viewModel::onTabSelected
            )

            if (uiState.activeTab == ShareRouteTab.PLAN) {
                ShareRouteDatePicker(
                    scheduledDate = uiState.scheduledDate,
                    onDateChanged = viewModel::onScheduledDateChanged
                )
            }

            ShareRouteSearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChanged,
                onClearQuery = { viewModel.onQueryChanged("") },
                onSearchAction = { focusManager.clearFocus() }
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoadingChats -> ShareRouteLoadingContent()
                    uiState.loadFailed -> ShareRouteErrorContent(
                        onRetry = { viewModel.onScreenOpened() }
                    )
                    uiState.filteredChats.isEmpty() -> ShareRouteEmptyContent(
                        hasQuery = uiState.query.isNotBlank()
                    )
                    else -> ShareRouteChatList(
                        chats = uiState.filteredChats,
                        onChatToggled = viewModel::onChatToggled
                    )
                }
            }

            ShareRouteBottomBar(
                activeTab = uiState.activeTab,
                canShare = uiState.canShare,
                isSending = uiState.isSending,
                sendError = uiState.sendError,
                selectedCount = uiState.selectedCount,
                onShareClicked = {
                    viewModel.onShareClicked(
                        originLat = originLat,
                        originLng = originLng,
                        destLat = destLat,
                        destLng = destLng,
                        originAddress = originAddress,
                        destAddress = destAddress,
                        distanceText = distanceText,
                        durationText = durationText,
                        routePriority = routePriority
                    )
                }
            )
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color(0xFFE1EBE4)),
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Enrere",
                    tint = Color(0xFF33413B)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.76f),
            border = BorderStroke(1.dp, Color(0xFFE1EBE4))
        ) {
            Text(
                text = appString(R.string.share_route_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

// ─── Tabs ─────────────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteTabs(
    activeTab: ShareRouteTab,
    onTabSelected: (ShareRouteTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShareRouteTab.entries.forEach { tab ->
            ShareRouteTabItem(
                tab = tab,
                isActive = tab == activeTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShareRouteTabItem(
    tab: ShareRouteTab,
    isActive: Boolean,
    onTabSelected: (ShareRouteTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF4FAF7D) else Color.White,
        animationSpec = tween(200),
        label = "tab_color_${tab.name}"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF5E6763),
        animationSpec = tween(200),
        label = "tab_content_color_${tab.name}"
    )

    Surface(
        modifier = modifier.clickable { onTabSelected(tab) },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF4FAF7D) else Color(0xFFDDE8E0)
        ),
        shadowElevation = if (isActive) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getTabIcon(tab),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = getTabText(tab),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun getTabIcon(tab: ShareRouteTab) = when (tab) {
    ShareRouteTab.SHARE -> Icons.Default.Share
    ShareRouteTab.PLAN -> Icons.Default.CalendarMonth
}

@Composable
private fun getTabText(tab: ShareRouteTab) = when (tab) {
    ShareRouteTab.SHARE -> appString(R.string.share_route_tab_share)
    ShareRouteTab.PLAN -> appString(R.string.share_route_tab_plan)
}

// ─── Date picker ──────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteDatePicker(
    scheduledDate: String?,
    onDateChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val placeholderText = appString(R.string.share_route_date_placeholder)
    val displayText = remember(scheduledDate, placeholderText) { formatScheduledDate(scheduledDate, placeholderText) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDateTimePicker(context, onDateChanged) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DatePickerIcon()
            DatePickerTextContent(
                displayText = displayText,
                hasDate = scheduledDate != null,
                modifier = Modifier.weight(1f)
            )
            if (scheduledDate != null) {
                ClearDateButton(onClear = { onDateChanged(null) })
            }
        }
    }
}

private fun formatScheduledDate(scheduledDate: String?, placeholderText: String): String {
    if (scheduledDate == null) return placeholderText
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val date = sdf.parse(scheduledDate)
        if (date != null) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        } else {
            scheduledDate
        }
    } catch (_: Exception) {
        scheduledDate
    }
}

private fun showDateTimePicker(context: android.content.Context, onDateChanged: (String?) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val cal = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val sdf = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        Locale.getDefault()
                    ).apply {
                        timeZone = TimeZone.getDefault()
                    }
                    onDateChanged(sdf.format(cal.time))
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.minDate = System.currentTimeMillis()
    }.show()
}

@Composable
private fun DatePickerIcon() {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = Color(0xFFEAF9EF)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF4FAF7D),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DatePickerTextContent(displayText: String, hasDate: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = appString(R.string.share_route_date_label),
            color = Color(0xFF8A9490),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = displayText,
            color = if (hasDate) Color(0xFF1F2C3B) else Color(0xFFADB8B3),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (hasDate) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ClearDateButton(onClear: () -> Unit) {
    IconButton(
        onClick = onClear,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = appString(R.string.cancel_action),
            tint = Color(0xFFADB8B3),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Search bar ───────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchAction: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        placeholder = {
            Text(
                text = appString(R.string.share_route_search_placeholder),
                color = Color(0xFFADB8B3),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF8A9490),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = appString(R.string.cancel_action),
                        tint = Color(0xFF8A9490),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4FAF7D),
            unfocusedBorderColor = Color(0xFFDDE8E0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

// ─── Chat list ────────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteChatList(
    chats: List<ShareRouteChatUiState>,
    onChatToggled: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = chats,
            key = { it.chatId }
        ) { chat ->
            ShareRouteChatItem(
                chat = chat,
                onToggle = { onChatToggled(chat.chatId) }
            )
        }
    }
}

@Composable
private fun ShareRouteChatItem(
    chat: ShareRouteChatUiState,
    onToggle: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (chat.isSelected) Color(0xFFEAF9EF) else Color.White,
        animationSpec = tween(200),
        label = "chat_item_color_${chat.chatId}"
    )
    val borderColor by animateColorAsState(
        targetValue = if (chat.isSelected) Color(0xFF4FAF7D) else Color(0xFFDDE8E0),
        animationSpec = tween(200),
        label = "chat_item_border_${chat.chatId}"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatAvatar(isGroup = chat.isGroup)
            ChatDetails(chat = chat, modifier = Modifier.weight(1f))
            ChatSelectionIndicator(isSelected = chat.isSelected)
        }
    }
}

@Composable
private fun ChatAvatar(isGroup: Boolean) {
    val bgColor = if (isGroup) Color(0xFFE8F0FB) else Color(0xFFEAF3EE)
    val borderColor = if (isGroup) Color(0xFFCBDCF5) else Color(0xFFD8ECDC)
    val icon = if (isGroup) Icons.Default.Group else Icons.Default.Person
    val tint = if (isGroup) Color(0xFF1A73E8) else Color(0xFF4FAF7D)

    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ChatDetails(chat: ShareRouteChatUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = chat.displayName,
            color = Color(0xFF1F2C3B),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (chat.isGroup) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = appString(R.string.share_route_participants, chat.participantCount),
                color = Color(0xFF8A9490),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ChatSelectionIndicator(isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = if (isSelected) Color(0xFF4FAF7D) else Color(0xFFF0F4F2),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF4FAF7D) else Color(0xFFCDD8D2)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── States ───────────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF4FAF7D),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ShareRouteErrorContent(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = appString(R.string.share_route_load_failed),
                    color = Color(0xFF3D4A45),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4FAF7D),
                        contentColor = Color.White
                    )
                ) {
                    Text(appString(R.string.share_route_retry), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ShareRouteEmptyContent(hasQuery: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = if (hasQuery) Icons.Default.Search else Icons.Default.Group,
                contentDescription = null,
                tint = Color(0xFFADB8B3),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (hasQuery) appString(R.string.share_route_empty_search) else appString(R.string.share_route_empty_chats),
                color = Color(0xFFADB8B3),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ─── Bottom bar ───────────────────────────────────────────────────────────────

@Composable
private fun ShareRouteBottomBar(
    activeTab: ShareRouteTab,
    canShare: Boolean,
    isSending: Boolean,
    sendError: Boolean,
    selectedCount: Int,
    onShareClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sendError) {
            ShareRouteErrorBanner()
        }

        Button(
            onClick = onShareClicked,
            enabled = canShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4FAF7D),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFCDE8DA),
                disabledContentColor = Color.White
            )
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = getTabIcon(activeTab),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getBottomBarText(activeTab, selectedCount),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ShareRouteErrorBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF0EE),
        border = BorderStroke(1.dp, Color(0xFFFFCFC9))
    ) {
        Text(
            text = appString(R.string.share_route_error),
            color = Color(0xFFB94040),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun getBottomBarText(activeTab: ShareRouteTab, selectedCount: Int): String {
    return when (selectedCount) {
        0 -> getTabText(activeTab)
        1 -> if (activeTab == ShareRouteTab.SHARE) appString(R.string.share_route_button_share_one) else appString(R.string.share_route_button_plan_one)
        else -> if (activeTab == ShareRouteTab.SHARE) appString(R.string.share_route_button_share_count, selectedCount) else appString(R.string.share_route_button_plan_count, selectedCount)
    }
}
