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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.appString
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun FriendSearchScreen(
    user: UserInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendSearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

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
                FriendSearchTopBar(
                    onBack = onBack
                )
            }

            item {
                FriendSearchInputCard(
                    query = uiState.query,
                    isSearching = uiState.isSearching,
                    onQueryChange = viewModel::onQueryChanged,
                    onClearQuery = { viewModel.onQueryChanged("") },
                    onSearchAction = {
                        focusManager.clearFocus()
                        viewModel.retrySearch()
                    }
                )
            }

            when {
                uiState.query.isBlank() -> {
                    item {
                        FriendSearchMessageCard(
                            title = appString(R.string.friend_search_empty_title),
                            description = appString(R.string.friend_search_empty_description),
                            icon = Icons.Default.Search,
                            accent = Color(0xFF6A9A7E)
                        )
                    }
                }

                uiState.isSearching -> {
                    item {
                        FriendSearchLoadingCard()
                    }
                }

                uiState.searchFailed -> {
                    item {
                        FriendSearchErrorCard(
                            onRetry = {
                                focusManager.clearFocus()
                                viewModel.retrySearch()
                            }
                        )
                    }
                }

                uiState.hasSearched && uiState.results.isEmpty() -> {
                    item {
                        FriendSearchMessageCard(
                            title = appString(R.string.friend_search_no_results_title),
                            description = appString(R.string.friend_search_no_results_description),
                            icon = Icons.Default.PersonAdd,
                            accent = Color(0xFF708C79)
                        )
                    }
                }

                else -> {
                    item {
                        FriendSearchSectionHeader(
                            text = appString(R.string.friend_search_results_title),
                            count = uiState.results.size
                        )
                    }

                    items(
                        items = uiState.results,
                        key = { result -> result.googleId }
                    ) { result ->
                        FriendSearchResultCard(
                            result = result,
                            onAddFriend = { viewModel.onAddFriendClicked(result.googleId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendSearchTopBar(
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
                    text = appString(R.string.friend_search_title),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(60.dp))
    }
}

@Composable
private fun FriendSearchInputCard(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
        shadowElevation = 10.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Text(
                text = appString(R.string.friend_search_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appString(R.string.friend_search_input_description),
                color = Color(0xFF61716A),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF7FBF8),
                border = BorderStroke(1.dp, Color(0xFFD5E4DB))
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    singleLine = true,
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = Color(0xFFE9F4ED)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF5E9F7A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF5E9F7A),
                                strokeWidth = 2.dp
                            )
                        } else if (query.isNotBlank()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = appString(R.string.close),
                                    tint = Color(0xFF7A8782)
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = appString(R.string.friend_search_placeholder),
                            color = Color(0xFF8A9791)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchAction() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color(0xFF5E9F7A)
                    )
                )
            }
        }
    }
}

@Composable
private fun FriendSearchLoadingCard() {
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
                    text = appString(R.string.friend_search_loading),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = appString(R.string.friend_search_input_description),
                    color = Color(0xFF6A7973),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun FriendSearchErrorCard(
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
                text = appString(R.string.friend_search_error_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appString(R.string.friend_search_error_description),
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
private fun FriendSearchMessageCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, Color(0xFFDDE8E0)),
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
                color = accent.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                color = Color(0xFF67756F),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FriendSearchResultCard(
    result: FriendSearchResultUiState,
    onAddFriend: () -> Unit
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
                    photoUrl = result.photoUrl,
                    username = result.username
                )

                Spacer(modifier = Modifier.width(14.dp))

                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = result.username,
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
                            text = result.email,
                            color = Color(0xFF67756F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isActionDisabled = result.isAdded || result.isRequestSent || result.isSubmittingRequest
            val buttonContainerColor = when {
                result.isAdded -> Color(0xFFB7D7C3)
                result.isRequestSent -> Color(0xFFDCE9E1)
                else -> Color(0xFF5E9F7A)
            }
            val buttonContentColor = when {
                result.isAdded -> Color(0xFF1E4F36)
                result.isRequestSent -> Color(0xFF42614F)
                else -> Color.White
            }

            Button(
                onClick = onAddFriend,
                enabled = !isActionDisabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor,
                    contentColor = buttonContentColor,
                    disabledContainerColor = buttonContainerColor,
                    disabledContentColor = buttonContentColor
                )
            ) {
                if (result.isSubmittingRequest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (result.isAdded || result.isRequestSent) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.PersonAdd
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            result.isAdded -> appString(R.string.friend_search_added)
                            result.isRequestSent -> appString(R.string.friend_search_request_sent)
                            else -> appString(R.string.friend_search_add_action)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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
private fun FriendSearchSectionHeader(
    text: String,
    count: Int
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
                color = Color(0xFFEAF4EE)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF6A9A7E),
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
                color = Color(0xFFEAF4EE)
            ) {
                Text(
                    text = count.toString(),
                    color = Color(0xFF5E9F7A),
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
