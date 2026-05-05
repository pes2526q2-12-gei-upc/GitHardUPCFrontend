package com.safesteps.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
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
                onSearchAction = { focusManager.clearFocus() }
            )
        }

        when {
            uiState.query.isBlank() -> {
                item {
                    FriendSearchMessageCard(
                        title = appString(R.string.friend_search_empty_title),
                        description = appString(R.string.friend_search_empty_description)
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
                        description = appString(R.string.friend_search_no_results_description)
                    )
                }
            }

            else -> {
                item {
                    Text(
                        text = appString(R.string.friend_search_results_title),
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
                text = appString(R.string.friend_search_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
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
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Text(
                text = appString(R.string.friend_search_input_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )


            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
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
                                contentDescription = appString(R.string.close)
                            )
                        }
                    }
                },
                placeholder = {
                    Text(text = appString(R.string.friend_search_placeholder))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchAction() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5E9F7A),
                    unfocusedBorderColor = Color(0xFFD7E5DC),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedLeadingIconColor = Color(0xFF5E9F7A),
                    unfocusedLeadingIconColor = Color(0xFF7A8782)
                )
            )
        }
    }
}

@Composable
private fun FriendSearchLoadingCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF5E9F7A),
                strokeWidth = 3.dp
            )
            Text(
                text = appString(R.string.friend_search_loading),
                color = Color(0xFF4F5F58),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun FriendSearchErrorCard(
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
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
                shape = RoundedCornerShape(16.dp),
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
    description: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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

            Button(
                onClick = onAddFriend,
                enabled = !result.isAdded && !result.isSubmittingRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (result.isAdded) Color(0xFFB7D7C3) else Color(0xFF5E9F7A),
                    contentColor = if (result.isAdded) Color(0xFF1E4F36) else Color.White,
                    disabledContainerColor = if (result.isAdded) Color(0xFFB7D7C3) else Color(0xFFD7E5DC),
                    disabledContentColor = if (result.isAdded) Color(0xFF1E4F36) else Color(0xFF66756F)
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
                        imageVector = if (result.isAdded) Icons.Default.Check else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (result.isAdded) {
                            appString(R.string.friend_search_added)
                        } else {
                            appString(R.string.friend_search_add_action)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        AsyncImage(
            model = photoUrl,
            contentDescription = username,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        return
    }

    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = Color(0xFFE8F0EB)
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
