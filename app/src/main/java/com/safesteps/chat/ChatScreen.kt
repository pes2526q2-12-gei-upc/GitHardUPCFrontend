package com.safesteps.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
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
import com.safesteps.profile.ChatListItemUiState
import com.safesteps.profile.ChatListViewModel
import com.safesteps.profile.ConversationViewModel
import com.safesteps.profile.FriendForChat
import com.safesteps.profile.MessageUiState



@Composable
fun UserAvatarSmall(username: String, photoUrl: String?, size: Int = 36) {
    val sizeDp = size.dp
    val cleanUrl = photoUrl?.trim()?.takeIf { it.isNotBlank() }

    // LOG per diagnosticar avatar — filtra per "AVATAR_UI" al Logcat
    android.util.Log.d("AVATAR_UI", "username='$username' url='$cleanUrl'")

    if (cleanUrl != null) {
        AsyncImage(
            model = cleanUrl,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clip(CircleShape),
            onError = { android.util.Log.e("AVATAR_UI", "Coil ERROR carregant: $cleanUrl → ${it.result.throwable?.message}") },
            onSuccess = { android.util.Log.d("AVATAR_UI", "Coil OK: $cleanUrl") }
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


@OptIn(ExperimentalMaterial3Api::class)
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
    var detailsChat by remember { mutableStateOf<ChatListItemUiState?>(null) }
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

    detailsChat?.let { chat ->
        ChatDetailsBottomSheet(
            chatName = chat.otherParticipantName,
            isGroup = chat.type == "GROUP",
            participants = chat.participantNames,
            onExitChat = { detailsChat = null; exitConfirmChatId = chat.chatId },
            onDismiss = { detailsChat = null }
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
                            onExitClick = { exitConfirmChatId = chat.chatId },
                            onInfoClick = { detailsChat = chat }
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
    onExitClick: () -> Unit,
    onInfoClick: () -> Unit
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
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    appString(R.string.chat_details_action),
                    tint = Color(0xFF9AA7A0),
                    modifier = Modifier.size(20.dp)
                )
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
    onExitChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(width = 40.dp, height = 4.dp).background(Color(0xFFDDE8E3), shape = RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(20.dp))

            if (isGroup) GroupAvatar(size = 64)
            else UserAvatarSmall(
                username = chatName,
                photoUrl = null,
                size = 64
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = chatName,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isGroup) appString(R.string.chat_group_label) else appString(R.string.chat_private_label),
                color = Color(0xFF9AA7A0),
                style = MaterialTheme.typography.bodySmall
            )

            if (participants.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = Color(0xFFEEF2EF)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        appString(R.string.chat_participants_label),
                        color = Color(0xFF67756F),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF5E9F7A).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${participants.size}",
                            color = Color(0xFF5E9F7A),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                participants.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarSmall(
                            username = name,
                            photoUrl = null,
                            size = 36
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            color = Color(0xFF23333A),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (isGroup) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = Color(0xFFEEF2EF)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onExitChat,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEDED))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription =  null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        appString(R.string.chat_exit_action),
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    chatId: Long,
    otherParticipantName: String,
    user: UserInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    val listState = rememberLazyListState()
    var showDetails by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.init(
            chatId = chatId,
            myGoogleId = user.googleId,
            myUsername = user.username,
            otherParticipantName = otherParticipantName
        )
    }
    DisposableEffect(chatId) {
        viewModel.onScreenVisible();
        onDispose { viewModel.onScreenHidden() }
    }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }
    // Re-scroll quan el teclat s'obre perquè el darrer missatge no es tapi
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && uiState.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    LaunchedEffect(user.googleId) {
        viewModel.carregarAvatarSiCal(user.googleId)
    }

    if (showDetails) {
        ChatDetailsBottomSheet(
            chatName = otherParticipantName, isGroup = uiState.isGroup,
            participants = uiState.participantNames,
            onExitChat = { showDetails = false; showExitConfirm = true },
            onDismiss = { showDetails = false }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(appString(R.string.chat_exit_title)) },
            text = { Text(appString(R.string.chat_exit_confirmation)) },
            confirmButton = {
                Button(
                    onClick = { showExitConfirm = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text(
                    text = appString(R.string.chat_exit_action),
                    color = Color.White
                ) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirm = false }
                ) {
                    Text(text = appString(R.string.cancel_action)) } }
        )
    }

    // NOTA: per al teclat, cal afegir a MainActivity.onCreate():
    //   WindowCompat.setDecorFitsSystemWindows(window, false)
    // Sense això, imePadding() reporta 0 i el camp no es mou sobre el teclat.
    Column(modifier = modifier
        .fillMaxSize()
        .background(Color(0xFFF4F7F5))
        .statusBarsPadding()
        .imePadding()           // ajusta quan apareix el teclat
        .navigationBarsPadding() // ajusta per a la barra de navegació
    ) {
        ConversationTopBar(
            title = otherParticipantName,
            onBack = onBack,
            onOptions = { showDetails = true }
        )
        when {
            uiState.isLoadingHistory ->
                Box(
                    Modifier.weight(1f).fillMaxWidth(), // fillMaxWidth per centrar correctament
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF5E9F7A))
                }
            uiState.loadFailed ->
                ChatErrorState(
                    modifier = Modifier.weight(1f),
                    message = appString(R.string.chat_messages_load_failed),
                    onRetry = viewModel::onScreenVisible
                )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = appString(R.string.chat_no_messages_yet),
                                color = Color(0xFF9AA7A0),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        } }
                } else {
                    items(uiState.messages, key = { it.localId ?: it.id.toString() }) { msg ->
                        MessageBubble(
                            message = msg,
                            avatarUrl = avatars[msg.senderGoogleId],
                            onLoadAvatar = { viewModel.carregarAvatarSiCal(msg.senderGoogleId) }
                        )
                    }
                }
            }
        }
        MessageInputBar(
            text = uiState.inputText,
            isSending = uiState.isSending,
            sendFailed = uiState.sendFailed,
            onTextChange = viewModel::onInputChanged,
            onSend = viewModel::sendMessage
        )
    }
}


@Composable
private fun ChatTopBarSimple(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = appString(R.string.back),
                    tint = Color(0xFF33413B)
                ) }
            Text(
                text = title,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun ConversationTopBar(title: String, onBack: () -> Unit, onOptions: () -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp).clickable(onClick = onOptions)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = appString(R.string.back),
                        tint = Color(0xFF33413B)
                    )
                }
                IconButton(
                    onClick = onOptions
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription =  appString(R.string.chat_details_action),
                        tint = Color(0xFF33413B)
                    )
                }
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
    val bubbleColor = if (isMe) Color(0xFF5E9F7A) else Color(0xFFE9EDF0)
    val textColor = if (isMe) Color.White else Color(0xFF23333A)

    LaunchedEffect(message.senderGoogleId) { onLoadAvatar() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            UserAvatarSmall(
                username = message.senderUsername,
                photoUrl = avatarUrl, size = 28);
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = 260.dp),
            shape =
                if (isMe)
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                else
                    RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = bubbleColor
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = textColor.copy(alpha = if (message.isOptimistic) 0.6f else 1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                // Timestamp + icona d'estat (read receipt visual)
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (message.createdAt.isNotBlank()) {
                        Text(
                            text = message.createdAt,
                            color = textColor.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            softWrap = false
                        )
                    }
                    // Mostrem l'estat NOMÉS als nostres missatges
                    if (message.isFromMe) {
                        when {
                            message.isOptimistic -> {
                                // Enviant: icona de rellotge
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Enviant",
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            else -> {
                                // Enviat i confirmat: ✓ (un sol tick verd/blanc)
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Enviat",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isMe) {
            Spacer(Modifier.width(6.dp));
            UserAvatarSmall(
                username = message.senderUsername,
                photoUrl = avatarUrl,
                size = 28
            ) }
    }
}


@Composable
private fun MessageInputBar(
    text: String,
    isSending: Boolean,
    sendFailed: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (sendFailed)
                Text(
                    text = appString(R.string.chat_send_failed),
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = appString(R.string.chat_input_placeholder),
                            color = Color(0xFF9AA7A0)
                        ) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5E9F7A),
                        unfocusedBorderColor = Color(0xFFDDE8E3),
                        focusedContainerColor = Color(0xFFF8FBF9),
                        unfocusedContainerColor = Color(0xFFF8FBF9)),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Surface(
                    Modifier.size(48.dp),
                    shape = CircleShape,
                    color =
                        if (text.isNotBlank() && !isSending) Color(0xFF5E9F7A)
                        else Color(0xFFDDE8E3)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSending)
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = Color(0xFF5E9F7A),
                                strokeWidth = 2.dp
                            )
                        else
                            IconButton(
                                onClick = onSend,
                                enabled = text.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription =  appString(R.string.chat_send_action),
                                    tint =
                                        if (text.isNotBlank())
                                            Color.White
                                        else
                                            Color(0xFF9AA7A0),
                                    modifier = Modifier.size(20.dp))
                            }
                    }
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
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF5E9F7A)) else null
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