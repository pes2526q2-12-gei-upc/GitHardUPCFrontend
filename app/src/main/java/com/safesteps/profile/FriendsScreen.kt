package com.safesteps.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.appString

@Composable
fun FriendsScreen(
    user: UserInfo,
    onBack: () -> Unit,
    onAddFriendClick: () -> Unit,
    onChatClick: (FriendListItemUiState) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var friendPendingRemoval by remember { mutableStateOf<FriendListItemUiState?>(null) }

    LaunchedEffect(user.googleId) {
        viewModel.onCurrentUserChanged(user)
        viewModel.onScreenOpened()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                FriendsTopBar(
                    onBack = onBack,
                    onAddFriendClick = onAddFriendClick,
                    pendingCount = uiState.pendingRequests.size,
                    friendCount = uiState.friends.size
                )
            }

            when {
                uiState.isLoading -> {
                    item {
                        FriendsLoadingCard()
                    }
                }

                uiState.loadFailed -> {
                    item {
                        FriendsErrorCard(onRetry = viewModel::retryLoad)
                    }
                }

                uiState.pendingRequests.isEmpty() && uiState.friends.isEmpty() -> {
                    item {
                        FriendsEmptyCard(onAddFriendClick = onAddFriendClick)
                    }
                }

                else -> {
                    if (uiState.pendingRequests.isNotEmpty()) {
                        item {
                            FriendsSectionHeader(
                                text = appString(R.string.friends_pending_title),
                                count = uiState.pendingRequests.size,
                                icon = Icons.Default.PersonAddAlt1,
                                tint = Color(0xFF6E9F82)
                            )
                        }

                        items(
                            items = uiState.pendingRequests,
                            key = { request -> request.googleId }
                        ) { request ->
                            PendingFriendRequestCard(
                                request = request,
                                onAcceptClick = { viewModel.onAcceptFriendRequestClicked(request.googleId) },
                                onDeclineClick = { viewModel.onDeclineFriendRequestClicked(request.googleId) }
                            )
                        }
                    }

                    if (uiState.friends.isNotEmpty()) {
                        item {
                            FriendsSectionHeader(
                                text = appString(R.string.friends_results_title),
                                count = uiState.friends.size,
                                icon = Icons.Default.ChatBubbleOutline,
                                tint = Color(0xFF4F7D66)
                            )
                        }

                        items(
                            items = uiState.friends,
                            key = { friend -> friend.googleId }
                        ) { friend ->
                            FriendItemCard(
                                friend = friend,
                                onChatClick = { onChatClick(friend) },
                                onRemoveClick = { friendPendingRemoval = friend }
                            )
                        }
                    }
                }
            }
        }

        val selectedFriend = friendPendingRemoval
        if (selectedFriend != null) {
            RemoveFriendConfirmationDialog(
                friendName = selectedFriend.username,
                onDismiss = { friendPendingRemoval = null },
                onConfirm = {
                    friendPendingRemoval = null
                    viewModel.onRemoveFriendClicked(selectedFriend.googleId)
                }
            )
        }
    }
}

@Composable
private fun FriendsTopBar(
    onBack: () -> Unit,
    onAddFriendClick: () -> Unit,
    pendingCount: Int,
    friendCount: Int
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(60.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    HeaderActionButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = appString(R.string.back)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.76f),
                        border = BorderStroke(1.dp, Color(0xFFE1EBE4))
                    ) {
                        Text(
                            text = appString(R.string.community_menu_friends_title),
                            color = Color(0xFF23333A),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier.width(60.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    HeaderActionButton(
                        onClick = onAddFriendClick,
                        icon = Icons.Default.PersonAddAlt1,
                        contentDescription = appString(R.string.friends_add_new_action),
                        tint = Color(0xFF5E9F7A)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
            shadowElevation = 10.dp
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Text(
                    text = appString(R.string.community_menu_friends_title),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = appString(R.string.community_menu_friends_description),
                    color = Color(0xFF61716A),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (pendingCount > 0 || friendCount > 0) {
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pendingCount > 0) {
                            FriendsMetricChip(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PersonAddAlt1,
                                label = appString(R.string.friends_pending_title),
                                value = pendingCount,
                                tint = Color(0xFF76A286)
                            )
                        }
                        if (friendCount > 0) {
                            FriendsMetricChip(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ChatBubbleOutline,
                                label = appString(R.string.friends_results_title),
                                value = friendCount,
                                tint = Color(0xFF577F6A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsLoadingCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF5E9F7A),
                strokeWidth = 3.dp
            )
            androidx.compose.foundation.layout.Column {
                Text(
                    text = appString(R.string.friends_loading),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = appString(R.string.community_menu_friends_description),
                    color = Color(0xFF6A7973),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun FriendsErrorCard(
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, Color(0xFFE8DAD4)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color(0xFFFFEDE6)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = Color(0xFFD2684C),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = appString(R.string.friends_error_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appString(R.string.friends_error_description),
                color = Color(0xFF67756F),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5E9F7A),
                    contentColor = Color.White
                )
            ) {
                Text(text = appString(R.string.retry_action))
            }
        }
    }
}

@Composable
private fun FriendsEmptyCard(
    onAddFriendClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, Color(0xFFDCE8E1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color(0xFFEAF4EE)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PersonAddAlt1,
                        contentDescription = null,
                        tint = Color(0xFF5E9F7A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = appString(R.string.friends_empty_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appString(R.string.friends_empty_description),
                color = Color(0xFF67756F),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddFriendClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5E9F7A),
                    contentColor = Color.White
                )
            ) {
                Text(text = appString(R.string.friends_add_new_action))
            }
        }
    }
}

@Composable
private fun PendingFriendRequestCard(
    request: PendingFriendRequestUiState,
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit
) {
    val isBusy = request.isAccepting || request.isDeclining

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(
                    photoUrl = request.photoUrl,
                    username = request.username
                )

                Spacer(modifier = Modifier.width(14.dp))

                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = request.username,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF7A8782),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = request.email,
                            color = Color(0xFF67756F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NegativeActionButton(
                    onClick = onDeclineClick,
                    enabled = !isBusy,
                    isLoading = request.isDeclining,
                    text = appString(R.string.friends_decline_action)
                )

                Button(
                    onClick = onAcceptClick,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5E9F7A),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFD7E5DC),
                        disabledContentColor = Color(0xFF66756F)
                    )
                ) {
                    if (request.isAccepting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appString(R.string.friends_accept_action),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendItemCard(
    friend: FriendListItemUiState,
    onChatClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(
                    photoUrl = friend.photoUrl,
                    username = friend.username
                )

                Spacer(modifier = Modifier.width(14.dp))

                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = friend.username,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF7A8782),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = friend.email,
                            color = Color(0xFF67756F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NegativeActionButton(
                    onClick = onRemoveClick,
                    enabled = !friend.isRemoving,
                    isLoading = friend.isRemoving,
                    text = appString(R.string.friends_remove_action)
                )

                OutlinedButton(
                    onClick = onChatClick,
                    enabled = !friend.isRemoving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFD3E4DA)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF7FBF8),
                        contentColor = Color(0xFF33413B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = appString(R.string.friends_chat_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun NegativeActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Color(0xFFF0C3BC) else Color(0xFFF5DEDA)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFFFF7F4),
            contentColor = Color(0xFFC24D37),
            disabledContainerColor = Color(0xFFFCEBE7),
            disabledContentColor = Color(0xFFD09B90)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color(0xFFC24D37),
                strokeWidth = 2.dp
            )
        } else {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = Color(0xFFFFE7E0)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFFC24D37)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RemoveFriendConfirmationDialog(
    friendName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFCFB),
        shape = RoundedCornerShape(28.dp),
        title = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFECE7)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = Color(0xFFD8563F),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = appString(R.string.friends_remove_confirmation_title),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF3F7F4),
                    border = BorderStroke(1.dp, Color(0xFFE0E9E3))
                ) {
                    Text(
                        text = friendName,
                        color = Color(0xFF2F3C36),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Text(
                    text = appString(R.string.friends_remove_confirmation_message, friendName),
                    color = Color(0xFF5C6A64),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFD8E3DC)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFFFFFFF),
                    contentColor = Color(0xFF58655F)
                )
            ) {
                Text(
                    text = appString(R.string.cancel_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD8563F),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = appString(R.string.friends_remove_confirm_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun HeaderActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color(0xFF33413B)
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFFE1EBE4)),
        shadowElevation = 8.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@Composable
private fun FriendsMetricChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = tint.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color(0xFF66756F),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value.toString(),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FriendsSectionHeader(
    text: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, Color(0xFFE1EBE4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = text,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.14f)
            ) {
                Text(
                    text = count.toString(),
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun FriendAvatar(
    photoUrl: String?,
    username: String
) {
    if (!photoUrl.isNullOrBlank()) {
        Surface(
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
            shadowElevation = 6.dp
        ) {
            AsyncImage(
                model = photoUrl,
                contentDescription = username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(3.dp)
                    .clip(CircleShape)
            )
        }
        return
    }

    Surface(
        modifier = Modifier.size(62.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
        shadowElevation = 6.dp
    ) {
        Surface(
            modifier = Modifier
                .padding(3.dp)
                .clip(CircleShape),
            shape = CircleShape,
            color = Color(0xFFEAF3EE)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF5E9F7A),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
