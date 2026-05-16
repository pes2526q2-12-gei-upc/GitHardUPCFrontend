package com.safesteps.chat

import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.appString
import com.safesteps.chat.ChatConversationViewModel


@Composable
fun UserAvatarSmall(username: String, photoUrl: String?, size: Int = 36) {
    val sizeDp = size.dp
    val cleanUrl = photoUrl?.trim()?.takeIf { it.isNotBlank() }

    Log.d("AVATAR_UI", "username='$username' url='$cleanUrl'")

    if (cleanUrl != null) {
        AsyncImage(
            model = cleanUrl,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clip(CircleShape),
            onError = { Log.e("AVATAR_UI", "Coil ERROR carregant: $cleanUrl → ${it.result.throwable?.message}") },
            onSuccess = { Log.d("AVATAR_UI", "Coil OK: $cleanUrl") }
        )
    } else {
        Surface(
            modifier = Modifier.size(sizeDp),
            shape = CircleShape,
            color = Color(0xFF6DD29A)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = username.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.38f).sp
                )
            }
        }
    }
}

@Composable
fun GroupAvatar(size: Int = 46) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = Color(0xFF507DBC).copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = Color(0xFF507DBC),
                modifier = Modifier.size((size * 0.56f).dp)
            )
        }
    }
}


@Composable
fun ChatListScreen(
    user: UserInfo,
    onBack: () -> Unit,
    onChatSelected: (chatId: Long, otherParticipantName: String) -> Unit,
    onCreateChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    var exitConfirmChatId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(user.googleId) {
        viewModel.onCurrentUserChanged(user.googleId, user.username)
        viewModel.onScreenOpened()
    }

    exitConfirmChatId?.let { chatId ->
        AlertDialog(
            onDismissRequest = { exitConfirmChatId = null },
            title = { Text(appString(R.string.chat_exit_title)) },
            text = { Text(appString(R.string.chat_exit_confirmation)) },
            confirmButton = {
                Button(onClick = { exitConfirmChatId = null; viewModel.exitChat(chatId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text(appString(R.string.chat_exit_action), color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { exitConfirmChatId = null }) { Text(appString(R.string.cancel_action)) } }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF4F7F5))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ChatTopBarSimple(
                title = appString(R.string.community_menu_chat_title),
                onBack = onBack
            )
            when {

                uiState.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF5E9F7A))
                }

                uiState.loadFailed -> ChatErrorState(
                    message = appString(R.string.chat_load_failed),
                    onRetry = viewModel::retryLoad)

                uiState.chats.isEmpty() -> ChatEmptyState()

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.chats, key = { it.chatId }) { chat ->
                        chat.otherParticipantGoogleId?.let { gid ->
                            LaunchedEffect(gid) { viewModel.loadAvatarIfNeeded(gid) }
                        }
                        ChatListItemCard(
                            chat = chat,
                            avatarUrl = chat.otherParticipantGoogleId?.let { avatars[it] },
                            onClick = { onChatSelected(chat.chatId, chat.otherParticipantName) },
                            onExitClick = { exitConfirmChatId = chat.chatId }
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            shape = CircleShape, color = Color(0xFF5E9F7A),
            shadowElevation = 6.dp
        ) {
            IconButton(
                onClick = onCreateChat,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    appString(R.string.chat_create_action),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp))
            }
        }
        AnimatedVisibility(
            visible = uiState.isOpeningChat,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun ChatListItemCard(
    chat: ChatListItemUiState,
    avatarUrl: String?,
    onClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val isGroup = chat.type == "GROUP"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isGroup) GroupAvatar(size = 46)
            else
                UserAvatarSmall(
                    username = chat.otherParticipantName,
                    photoUrl = avatarUrl,
                    size = 46
                )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = chat.otherParticipantName,
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isGroup) appString(R.string.chat_group_label) else appString(R.string.chat_tap_to_open),
                    color = Color(0xFF9AA7A0),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isGroup) {
                IconButton(
                    onClick = onExitClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        appString(R.string.chat_exit_action),
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun ChatConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFF5E9F7A),
    cancelText: String = appString(R.string.cancel_action),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFFFFF),
            contentColor = Color(0xFF23333A),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(iconTint.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = iconTint, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF23333A),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = message,
                    color = Color(0xFF5E6B73),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFDDE8E3)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF23333A))
                    ) {
                        Text(cancelText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
                    ) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatDetailsBottomSheet(
    chatName: String,
    isGroup: Boolean,
    participants: List<String>,
    participantGoogleIds: List<String> = emptyList(),
    avatars: Map<String, String> = emptyMap(),
    myGoogleId: String = "",
    isProcessing: Boolean = false,
    creatorGoogleId: String? = null,
    adminGoogleIds: Set<String> = emptySet(),
    currentUserIsAdmin: Boolean = false,
    currentUserIsCreator: Boolean = false,
    addableFriends: List<FriendForChat> = emptyList(),
    isLoadingAddableFriends: Boolean = false,
    onRequestLoadAddable: () -> Unit = {},
    onAdd: (googleId: String, username: String) -> Unit = { _, _ -> },
    onClearAddable: () -> Unit = {},
    onRequestAvatar: (googleId: String) -> Unit = {},
    onExitChat: () -> Unit,
    onKick: (googleId: String, username: String) -> Unit = { _, _ -> },
    onPromote: (googleId: String, username: String) -> Unit = { _, _ -> },
    onRevokeAdmin: (googleId: String, username: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kickConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var promoteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var revokeConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(participantGoogleIds) {
        participantGoogleIds.filter { it.isNotBlank() }.forEach { onRequestAvatar(it) }
    }

    ChatRoleDialogs(
        kickConfirm = kickConfirm,
        promoteConfirm = promoteConfirm,
        revokeConfirm = revokeConfirm,
        onKick = { gid, name -> kickConfirm = null; onKick(gid, name) },
        onPromote = { gid, name -> promoteConfirm = null; onPromote(gid, name) },
        onRevoke = { gid, name -> revokeConfirm = null; onRevokeAdmin(gid, name) },
        onDismissKick = { kickConfirm = null },
        onDismissPromote = { promoteConfirm = null },
        onDismissRevoke = { revokeConfirm = null }
    )

    if (showAddDialog) {
        AddParticipantDialog(
            isLoading = isLoadingAddableFriends,
            friends = addableFriends,
            avatars = avatars,
            onAdd = { gid, name -> showAddDialog = false; onAdd(gid, name); onClearAddable() },
            onDismiss = { showAddDialog = false; onClearAddable() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFFFFFF),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = Color(0xFF5E9F7A), trackColor = Color(0xFF5E9F7A).copy(alpha = 0.15f))
            }
            Box(modifier = Modifier.padding(top = 12.dp).size(40.dp, 4.dp).background(Color(0xFFDDE8E3), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(20.dp))

            ChatDetailsHeader(
                chatName = chatName, isGroup = isGroup, participantsCount = participants.size,
                participantGoogleIds = participantGoogleIds, myGoogleId = myGoogleId, avatars = avatars
            )

            if (isGroup) {
                Text(
                    text = appString(R.string.chat_members_label), modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF23333A), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(10.dp))

                participants.forEachIndexed { idx, name ->
                    val gid = participantGoogleIds.getOrNull(idx) ?: ""
                    ChatParticipantRow(
                        name = name, gid = gid, myGoogleId = myGoogleId, creatorGoogleId = creatorGoogleId,
                        adminGoogleIds = adminGoogleIds, avatars = avatars, currentUserIsAdmin = currentUserIsAdmin,
                        currentUserIsCreator = currentUserIsCreator, isProcessing = isProcessing,
                        onPromote = { promoteConfirm = it }, onRevoke = { revokeConfirm = it }, onKick = { kickConfirm = it }
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFFEEF2EF))
                Spacer(Modifier.height(16.dp))

                ChatDetailsActionButtons(
                    currentUserIsAdmin = currentUserIsAdmin, currentUserIsCreator = currentUserIsCreator,
                    isProcessing = isProcessing, onAddClick = { onRequestLoadAddable(); showAddDialog = true },
                    onExitClick = onExitChat
                )
            }
        }
    }
}

@Composable
private fun ChatDetailsHeader(
    chatName: String, isGroup: Boolean, participantsCount: Int,
    participantGoogleIds: List<String>, myGoogleId: String, avatars: Map<String, String>
) {
    if (isGroup) {
        GroupAvatar(size = 64)
    } else {
        val otherGid = participantGoogleIds.firstOrNull { it != myGoogleId && it.isNotBlank() }
        UserAvatarSmall(username = chatName, photoUrl = avatars[otherGid], size = 64)
    }
    Spacer(Modifier.height(12.dp))
    Text(chatName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF23333A))
    Spacer(Modifier.height(4.dp))
    Text(
        if (isGroup) appString(R.string.chat_group_subtitle, participantsCount) else appString(R.string.chat_private_subtitle),
        color = Color(0xFF9AA7A0), style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = Color(0xFFEEF2EF))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChatParticipantRow(
    name: String, gid: String, myGoogleId: String, creatorGoogleId: String?,
    adminGoogleIds: Set<String>, avatars: Map<String, String>, currentUserIsAdmin: Boolean,
    currentUserIsCreator: Boolean, isProcessing: Boolean,
    onPromote: (Pair<String, String>) -> Unit, onRevoke: (Pair<String, String>) -> Unit, onKick: (Pair<String, String>) -> Unit
) {
    val isMe = gid == myGoogleId
    val isCreator = gid.isNotBlank() && gid == creatorGoogleId
    val isAdmin = gid in adminGoogleIds
    val canManage = (currentUserIsAdmin || currentUserIsCreator) && !isMe && gid.isNotBlank()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatarSmall(username = name, photoUrl = avatars[gid], size = 38)
        Spacer(Modifier.width(10.dp))

        ParticipantInfo(
            name = name, isMe = isMe, isCreator = isCreator, isAdmin = isAdmin,
            modifier = Modifier.weight(1f)
        )

        if (canManage) {
            ParticipantManagementActions(
                gid = gid, name = name, isAdmin = isAdmin, isCreator = isCreator,
                currentUserIsCreator = currentUserIsCreator, isProcessing = isProcessing,
                onPromote = onPromote, onRevoke = onRevoke, onKick = onKick
            )
        }
    }
}

@Composable
private fun ParticipantInfo(
    name: String, isMe: Boolean, isCreator: Boolean, isAdmin: Boolean, modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name, color = Color(0xFF23333A),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
            )
            if (isMe) {
                Spacer(Modifier.width(6.dp))
                Text(appString(R.string.chat_self_indicator), color = Color(0xFF9AA7A0), style = MaterialTheme.typography.labelSmall)
            }
        }
        ParticipantRoleBadge(isCreator = isCreator, isAdmin = isAdmin)
    }
}

@Composable
private fun ParticipantRoleBadge(isCreator: Boolean, isAdmin: Boolean) {
    if (isCreator) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(appString(R.string.chat_role_leader), color = Color(0xFFB8860B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    } else if (isAdmin) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(appString(R.string.chat_role_admin), color = Color(0xFFF5A623), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParticipantManagementActions(
    gid: String, name: String, isAdmin: Boolean, isCreator: Boolean,
    currentUserIsCreator: Boolean, isProcessing: Boolean,
    onPromote: (Pair<String, String>) -> Unit, onRevoke: (Pair<String, String>) -> Unit, onKick: (Pair<String, String>) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isAdmin) {
            PromoteButton(isProcessing) { onPromote(gid to name) }
        } else if (currentUserIsCreator) {
            RevokeButton(isProcessing) { onRevoke(gid to name) }
        }

        if (!isCreator) {
            KickButton(isProcessing) { onKick(gid to name) }
        }
    }
}


@Composable
private fun PromoteButton(isProcessing: Boolean, onClick: () -> Unit) {
    ChatActionButton(
        icon = Icons.Default.Star,
        activeTint = Color(0xFFF5A623),
        isProcessing = isProcessing,
        onClick = onClick
    )
}

@Composable
private fun RevokeButton(isProcessing: Boolean, onClick: () -> Unit) {
    ChatActionButton(
        icon = Icons.Default.StarBorder,
        activeTint = Color(0xFF9AA7A0),
        isProcessing = isProcessing,
        onClick = onClick
    )
}

@Composable
private fun KickButton(isProcessing: Boolean, onClick: () -> Unit) {
    ChatActionButton(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        activeTint = Color(0xFFD32F2F),
        isProcessing = isProcessing,
        onClick = onClick
    )
}


@Composable
private fun ChatActionButton(
    icon: ImageVector,
    activeTint: Color,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = !isProcessing,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isProcessing) Color(0xFFCCCCCC) else activeTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ChatRoleDialogs(
    kickConfirm: Pair<String, String>?, promoteConfirm: Pair<String, String>?, revokeConfirm: Pair<String, String>?,
    onKick: (String, String) -> Unit, onPromote: (String, String) -> Unit, onRevoke: (String, String) -> Unit,
    onDismissKick: () -> Unit, onDismissPromote: () -> Unit, onDismissRevoke: () -> Unit
) {
    kickConfirm?.let { (gid, name) ->
        ChatConfirmDialog(
            title = appString(R.string.chat_kick_title, name), message = appString(R.string.chat_kick_confirmation, name),
            confirmText = appString(R.string.chat_kick_action), confirmColor = Color(0xFFD32F2F),
            icon = Icons.AutoMirrored.Filled.ExitToApp, iconTint = Color(0xFFD32F2F),
            onConfirm = { onKick(gid, name) }, onDismiss = onDismissKick
        )
    }
    promoteConfirm?.let { (gid, name) ->
        ChatConfirmDialog(
            title = appString(R.string.chat_promote_title, name), message = appString(R.string.chat_promote_confirmation, name),
            confirmText = appString(R.string.chat_promote_action), confirmColor = Color(0xFF5E9F7A),
            icon = Icons.Default.Star, iconTint = Color(0xFFF5A623),
            onConfirm = { onPromote(gid, name) }, onDismiss = onDismissPromote
        )
    }
    revokeConfirm?.let { (gid, name) ->
        ChatConfirmDialog(
            title = appString(R.string.chat_revoke_title, name), message = appString(R.string.chat_revoke_confirmation, name),
            confirmText = appString(R.string.chat_revoke_action), confirmColor = Color(0xFFD32F2F),
            icon = Icons.Default.StarBorder, iconTint = Color(0xFFD32F2F),
            onConfirm = { onRevoke(gid, name) }, onDismiss = onDismissRevoke
        )
    }
}

@Composable
private fun ChatDetailsActionButtons(
    currentUserIsAdmin: Boolean, currentUserIsCreator: Boolean,
    isProcessing: Boolean, onAddClick: () -> Unit, onExitClick: () -> Unit
) {
    if (currentUserIsAdmin || currentUserIsCreator) {
        Button(
            onClick = onAddClick, enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5EE), disabledContainerColor = Color(0xFFEEEEEE))
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = if (isProcessing) Color(0xFFCCCCCC) else Color(0xFF2E7D5B), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(appString(R.string.chat_add_participant_action), color = if (isProcessing) Color(0xFFCCCCCC) else Color(0xFF2E7D5B), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
    }
    Button(
        onClick = onExitClick, modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEDED))
    ) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(appString(R.string.chat_exit_action), color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddParticipantDialog(
    isLoading: Boolean, friends: List<FriendForChat>, avatars: Map<String, String>,
    onAdd: (String, String) -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(min = 200.dp, max = 520.dp).padding(8.dp),
            shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFFFF), shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).background(Color(0xFF5E9F7A).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF2E7D5B))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(appString(R.string.chat_add_to_group_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF23333A))
                }
                Spacer(Modifier.height(16.dp))
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF5E9F7A)) }
                    friends.isEmpty() -> Text(appString(R.string.chat_no_more_friends_to_add), color = Color(0xFF9AA7A0), modifier = Modifier.padding(vertical = 24.dp))
                    else -> LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(friends, key = { it.googleId.ifBlank { it.username } }) { f ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { onAdd(f.googleId, f.username) }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatarSmall(username = f.username, photoUrl = avatars[f.googleId], size = 40)
                                Spacer(Modifier.width(12.dp))
                                Text(f.username, modifier = Modifier.weight(1f), color = Color(0xFF23333A), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.Add, appString(R.string.chat_add_action), tint = Color(0xFF5E9F7A))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(appString(R.string.close), color = Color(0xFF5E9F7A), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    chatId: Long,
    otherParticipantName: String,
    user: UserInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDetails by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var notAdminMsg by remember { mutableStateOf<String?>(null) }

    ConversationSideEffects(viewModel, chatId, user, otherParticipantName, snackbarHostState) { notAdminMsg = it }

    notAdminMsg?.let { msg ->
        ChatConfirmDialog(
            title = appString(R.string.chat_action_not_allowed), message = msg,
            confirmText = appString(R.string.understood_action), confirmColor = Color(0xFFF5A623),
            icon = Icons.Default.Info, iconTint = Color(0xFFF5A623), cancelText = "",
            onConfirm = { notAdminMsg = null }, onDismiss = { notAdminMsg = null }
        )
    }

    if (showDetails) {
        ChatDetailsBottomSheet(
            chatName = otherParticipantName, isGroup = uiState.isGroup,
            participants = uiState.participantNames, participantGoogleIds = uiState.participantGoogleIds,
            avatars = avatars, myGoogleId = user.googleId, isProcessing = uiState.isProcessingAdmin,
            creatorGoogleId = uiState.creatorGoogleId, adminGoogleIds = uiState.adminGoogleIds,
            currentUserIsAdmin = uiState.currentUserIsAdmin, currentUserIsCreator = uiState.currentUserIsCreator,
            addableFriends = uiState.addableFriends, isLoadingAddableFriends = uiState.isLoadingAddableFriends,
            onRequestLoadAddable = viewModel::loadFriendsToAdd, onAdd = viewModel::addParticipant,
            onClearAddable = viewModel::clearAddableFriends, onRequestAvatar = viewModel::carregarAvatarSiCal,
            onExitChat = { showDetails = false; showExitConfirm = true },
            onKick = viewModel::kickParticipant, onPromote = viewModel::promoteToAdmin,
            onRevokeAdmin = viewModel::revokeAdmin, onDismiss = { showDetails = false }
        )
    }

    if (showExitConfirm) {
        ChatConfirmDialog(
            title = appString(R.string.chat_exit_title), message = appString(R.string.chat_exit_confirmation),
            confirmText = appString(R.string.chat_exit_action), confirmColor = Color(0xFFD32F2F),
            icon = Icons.AutoMirrored.Filled.ExitToApp, iconTint = Color(0xFFD32F2F),
            cancelText = appString(R.string.cancel_action),
            onConfirm = { showExitConfirm = false; onBack() }, onDismiss = { showExitConfirm = false }
        )
    }

    val isSwitchingChat = uiState.otherParticipantName != otherParticipantName
    val showFullLoader = isSwitchingChat || !uiState.isReady

    key(chatId) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data, containerColor = Color(0xFF33413B), contentColor = Color.White) } },
            containerColor = Color(0xFFF4F7F5),
            contentWindowInsets = WindowInsets(0)
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                ConversationContent(
                    uiState = uiState, avatars = avatars, listState = listState,
                    otherParticipantName = otherParticipantName,
                    onBack = onBack, onOptions = { showDetails = true },
                    onInputChanged = viewModel::onInputChanged, onSendMessage = viewModel::sendMessage,
                    onRetryLoad = viewModel::onScreenVisible, onLoadAvatar = viewModel::carregarAvatarSiCal
                )

                if (showFullLoader) {
                    ConversationLoadingOverlay()
                }
            }
        }
    }
}

@Composable
private fun ConversationSideEffects(
    viewModel: ChatConversationViewModel,
    chatId: Long,
    user: UserInfo,
    otherParticipantName: String,
    snackbarHostState: SnackbarHostState,
    onNotAdminError: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val missatge = when (event) {
                is ConversationEvent.Success -> context.getString(event.msgResId, *event.args.toTypedArray())
                is ConversationEvent.Error -> context.getString(event.msgResId, *event.args.toTypedArray())
                is ConversationEvent.Info -> context.getString(event.msgResId, *event.args.toTypedArray())
                is ConversationEvent.NotAdmin -> { onNotAdminError(context.getString(event.msgResId, *event.args.toTypedArray())); null }
            }
            missatge?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(chatId) { viewModel.init(chatId, user.googleId, user.username, otherParticipantName) }
    DisposableEffect(chatId) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }
    LaunchedEffect(user.googleId) { viewModel.carregarAvatarSiCal(user.googleId) }
}

@Composable
private fun ConversationContent(
    uiState: ConversationUiState, avatars: Map<String, String>, listState: LazyListState,
    otherParticipantName: String, onBack: () -> Unit, onOptions: () -> Unit,
    onInputChanged: (String) -> Unit, onSendMessage: () -> Unit,
    onRetryLoad: () -> Unit, onLoadAvatar: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7F5)).statusBarsPadding().imePadding()) {
        ConversationTopBar(
            title = otherParticipantName, isGroup = uiState.isGroup,
            avatarUrl = uiState.otherParticipantGoogleId?.let { avatars[it] },
            onBack = onBack, onOptions = onOptions
        )
        if (uiState.loadFailed) {
            ChatErrorState(modifier = Modifier.weight(1f), message = appString(R.string.chat_messages_load_failed), onRetry = onRetryLoad)
        } else {
            ConversationMessagesList(uiState.messages, avatars, listState, onLoadAvatar, Modifier.weight(1f))
        }
        MessageInputBar(
            text = uiState.inputText, isSending = uiState.isSending, sendFailed = uiState.sendFailed,
            onTextChange = onInputChanged, onSend = onSendMessage
        )
    }
}

@Composable
private fun ConversationMessagesList(
    messages: List<MessageUiState>, avatars: Map<String, String>, listState: LazyListState,
    onLoadAvatar: (String) -> Unit, modifier: Modifier
) {
    LazyColumn(
        modifier = modifier, state = listState, reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
                    Text(appString(R.string.chat_no_messages_yet), color = Color(0xFF9AA7A0), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(messages.reversed(), key = { it.localId ?: it.id.toString() }) { msg ->
                MessageBubble(message = msg, avatarUrl = avatars[msg.senderGoogleId], onLoadAvatar = { onLoadAvatar(msg.senderGoogleId) })
            }
        }
    }
}

@Composable
private fun ConversationLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7F5)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF5E9F7A), strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(16.dp))
            Text(appString(R.string.chat_loading_conversation), color = Color(0xFF5E6B73), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}



@Composable
private fun ChatTopBarSimple(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(Color.Transparent).padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(60.dp), Alignment.CenterStart) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, appString(R.string.back), tint = Color(0xFF33413B))
                }
            }
            Box(Modifier.weight(1f), Alignment.Center) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(0.76f),
                    border = BorderStroke(1.dp, Color(0xFFE1EBE4))) {
                    Text(title, color = Color(0xFF23333A), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
            Spacer(Modifier.width(60.dp))
        }
    }
}

@Composable
private fun ConversationTopBar(
    title: String, isGroup: Boolean = false, avatarUrl: String? = null,
    onBack: () -> Unit, onOptions: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, appString(R.string.back), tint = Color(0xFF33413B))
            }
            Row(modifier = Modifier.weight(1f).clickable(onClick = onOptions),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                if (isGroup) GroupAvatar(size = 30) else UserAvatarSmall(username = title, photoUrl = avatarUrl, size = 30)
                Spacer(Modifier.width(6.dp))
                Text(title, color = Color(0xFF23333A), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp))
            }
            IconButton(onClick = onOptions) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription =  appString(R.string.chat_details_action),
                    tint = Color(0xFF33413B)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageUiState,
    avatarUrl: String?,
    onLoadAvatar: () -> Unit
) {
    val isMe = message.isFromMe
    var showSenderInfo by remember { mutableStateOf(false) }

    LaunchedEffect(message.senderGoogleId) { onLoadAvatar() }

    if (showSenderInfo && !isMe) {
        SenderInfoDialog(
            username = message.senderUsername,
            avatarUrl = avatarUrl,
            onDismiss = { showSenderInfo = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            UserAvatarSmall(username = message.senderUsername, photoUrl = avatarUrl, size = 28)
            Spacer(Modifier.width(6.dp))
        }

        MessageBubbleContent(
            message = message,
            isMe = isMe,
            onClick = { if (!isMe) showSenderInfo = true }
        )

        if (isMe) {
            Spacer(Modifier.width(6.dp))
            UserAvatarSmall(username = message.senderUsername, photoUrl = avatarUrl, size = 28)
        }
    }
}

@Composable
private fun MessageBubbleContent(message: MessageUiState, isMe: Boolean, onClick: () -> Unit) {
    val bubbleColor = if (isMe) Color(0xFF5E9F7A) else Color(0xFFE9EDF0)
    val textColor = if (isMe) Color.White else Color(0xFF23333A)

    Surface(
        modifier = Modifier.widthIn(max = 260.dp).clickable(onClick = onClick),
        shape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color = bubbleColor
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                color = textColor.copy(alpha = if (message.isOptimistic) 0.6f else 1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(3.dp))

            MessageStatusRow(
                message = message,
                isMe = isMe,
                textColor = textColor,
                modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start)
            )
        }
    }
}

@Composable
private fun MessageStatusRow(
    message: MessageUiState,
    isMe: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.createdAt.isNotBlank()) {
            Text(
                text = message.createdAt,
                color = textColor.copy(alpha = 0.55f),
                fontSize = 10.sp,
                softWrap = false
            )
        }
        if (isMe) {
            Icon(
                imageVector = if (message.isOptimistic) Icons.Default.Schedule else Icons.Default.Done,
                contentDescription = null,
                tint = textColor.copy(alpha = if (message.isOptimistic) 0.5f else 0.7f),
                modifier = Modifier.size(if (message.isOptimistic) 11.dp else 14.dp)
            )
        }
    }
}

@Composable
private fun SenderInfoDialog(username: String, avatarUrl: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).padding(8.dp),
            shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                UserAvatarSmall(username = username, photoUrl = avatarUrl, size = 72)
                Spacer(Modifier.height(14.dp))
                Text(username.ifBlank { appString(R.string.chat_unknown_sender) }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(appString(R.string.chat_message_sender_label), color = Color(0xFF9AA7A0), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E9F7A))) {
                    Text(appString(R.string.close), color = Color.White)
                }
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageInputBar(
    text: String,
    isSending: Boolean,
    sendFailed: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val imeVisible = WindowInsets.isImeVisible
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (!imeVisible) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (sendFailed) {
                Text(
                    text = appString(R.string.chat_send_failed),
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(appString(R.string.chat_input_placeholder), color = Color(0xFF9AA7A0)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5E9F7A),
                        unfocusedBorderColor = Color(0xFFDDE8E3),
                        focusedContainerColor = Color(0xFFF8FBF9),
                        unfocusedContainerColor = Color(0xFFF8FBF9)
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )

                SendMessageButton(
                    isSending = isSending,
                    isEnabled = text.isNotBlank(),
                    onSend = onSend
                )
            }
        }
    }
}

@Composable
private fun SendMessageButton(isSending: Boolean, isEnabled: Boolean, onSend: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (isEnabled && !isSending) Color(0xFF5E9F7A) else Color(0xFFDDE8E3)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSending) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF5E9F7A), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSend, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = appString(R.string.chat_send_action),
                        tint = if (isEnabled) Color.White else Color(0xFF9AA7A0),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun CreateChatScreen(
    user: UserInfo,
    onBack: () -> Unit,
    onChatCreated: (chatId: Long, chatName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(user.googleId) {
        viewModel.onCurrentUserChanged(user.googleId, user.username);
        viewModel.loadFriendsForNewChat()
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF4F7F5)).statusBarsPadding().navigationBarsPadding()) {

        ChatTopBarSimple(
            title = appString(R.string.chat_create_title),
            onBack = onBack
        )

        when {
            uiState.isLoadingFriends ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF5E9F7A))
                }

            uiState.friendsForNewChat.isEmpty() ->
                ChatErrorState(
                    message = appString(R.string.chat_no_friends_to_chat),
                    onRetry = viewModel::loadFriendsForNewChat
                )

            else ->
                CreateChatContent(
                    friends = uiState.friendsForNewChat,
                    selectedFriendIds = uiState.selectedFriendIds,
                    groupName = uiState.newChatGroupName,
                    isCreating = uiState.isCreatingChat,
                    onFriendToggle = viewModel::toggleFriendSelection,
                    onGroupNameChange = viewModel::onGroupNameChanged,
                    onCreate = {
                        viewModel.createChat {
                                chatId,
                                chatName ->
                            onChatCreated(chatId, chatName)
                        }
                    }
                )
        }
    }
}

@Composable
private fun CreateChatContent(
    friends: List<FriendForChat>,
    selectedFriendIds: Set<String>,
    groupName: String,
    isCreating: Boolean,
    onFriendToggle: (String) -> Unit,
    onGroupNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val isGroup = selectedFriendIds.size > 1
    val canCreate = selectedFriendIds.isNotEmpty() && (!isGroup || groupName.isNotBlank())

    Column(
        modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isGroup) {
            OutlinedTextField(
                value = groupName,
                onValueChange = onGroupNameChange,
                label = { Text(appString(R.string.chat_group_name_label)) },
                placeholder = { Text(appString(R.string.chat_group_name_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5E9F7A), unfocusedBorderColor = Color(0xFFDDE8E3)))
        }

        if (selectedFriendIds.isNotEmpty()) {
            Text(
                text = "${selectedFriendIds.size} ${appString(R.string.chat_members_selected)}",
                color = Color(0xFF5E9F7A),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends, key = { it.googleId }) { friend ->
                FriendSelectableItem(
                    friend = friend,
                    isSelected = friend.googleId in selectedFriendIds,
                    onClick = { onFriendToggle(friend.googleId) }
                )
            }
        }
        Button(
            onClick = onCreate,
            enabled = canCreate && !isCreating,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(54.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E9F7A))
        ) {
            if (isCreating)
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            else
                Text(
                    text = if (isGroup) appString(R.string.chat_create_group_action) else appString(R.string.chat_create_private_action),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
        }
    }
}

@Composable
private fun FriendSelectableItem(
    friend: FriendForChat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE8F5EE) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF5E9F7A)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarSmall(
                username = friend.username,
                photoUrl = null,
                size = 40
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = friend.username,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isSelected)
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF5E9F7A),
                    modifier = Modifier.size(20.dp)
                )
        }
    }
}


@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                null,
                tint = Color(0xFFBFCEC8),
                modifier = Modifier.size(56.dp)
            )
            Text(
                appString(R.string.chat_no_conversations),
                color = Color(0xFF9AA7A0),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                appString(R.string.chat_start_hint),
                color = Color(0xFFBFCEC8),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                message,
                color = Color(0xFF9AA7A0),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E9F7A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(appString(R.string.retry_action))
            }
        }
    }
}