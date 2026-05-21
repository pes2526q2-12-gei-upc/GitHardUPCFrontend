package com.safesteps.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.data.EmergencyContactProfile
import com.safesteps.data.cargarPerfilPublicoUsuario
import com.safesteps.i18n.appString

private data class FriendPublicProfileLoadState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val profile: EmergencyContactProfile? = null
)

@Composable
fun FriendProfileScreen(
    friend: FriendListItemUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var refreshVersion by remember(friend.googleId) { mutableIntStateOf(0) }
    var loadState by remember(friend.googleId) {
        mutableStateOf(FriendPublicProfileLoadState())
    }

    LaunchedEffect(friend.googleId, refreshVersion) {
        loadState = loadState.copy(isLoading = true, loadFailed = false)
        loadState = runCatching {
            FriendPublicProfileLoadState(
                isLoading = false,
                loadFailed = false,
                profile = cargarPerfilPublicoUsuario(friend.googleId)
            )
        }.getOrElse {
            FriendPublicProfileLoadState(
                isLoading = false,
                loadFailed = true,
                profile = null
            )
        }
    }

    val publicProfile = loadState.profile
    val username = publicProfile?.username ?: friend.username
    val photoUrl = publicProfile?.photoUrl ?: friend.photoUrl
    val level = publicProfile?.level
    val points = publicProfile?.points

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        FriendProfileTopBar(
            title = appString(R.string.friends_profile_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FriendProfileHeroCard(
                username = username,
                email = friend.email,
                photoUrl = photoUrl,
                isEmergencyContact = friend.isEmergencyContact,
                isLoading = loadState.isLoading,
                level = level,
                points = points
            )

            FriendProfileDetailsCard(
                email = friend.email,
                isEmergencyContact = friend.isEmergencyContact,
                level = level,
                points = points
            )

            when {
                loadState.isLoading -> {
                    FriendProfileLoadingCard()
                }

                loadState.loadFailed -> {
                    FriendProfileErrorCard(
                        onRetry = { refreshVersion += 1 }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendProfileTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(60.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            FriendProfileActionButton(
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
                    text = title,
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
private fun FriendProfileHeroCard(
    username: String,
    email: String,
    photoUrl: String?,
    isEmergencyContact: Boolean,
    isLoading: Boolean,
    level: Long?,
    points: Long?
) {
    val safeLevel = (level ?: 1L).coerceAtLeast(1L).toInt()
    val tier = ((safeLevel - 1) / 5).coerceIn(0, 3)
    val tierColor = when (tier) {
        0 -> Color(0xFFB0BEC5)
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFF00E676)
        else -> Color(0xFFE040FB)
    }
    val borderWidth = if (tier == 0) 2.dp else (tier + 2).dp
    val pointsForCurrentLevel = ((safeLevel - 1).toLong() * 10L).let { it * it }
    val pointsForNextLevel = (safeLevel.toLong() * 10L).let { it * it }
    val pointsInCurrentLevel = (points?.minus(pointsForCurrentLevel) ?: 0L).coerceAtLeast(0L)
    val pointsNeededForNext = (pointsForNextLevel - pointsForCurrentLevel).coerceAtLeast(1L)
    val progress = if (points != null) {
        (pointsInCurrentLevel.toFloat() / pointsNeededForNext.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(borderWidth, tierColor),
                    modifier = Modifier.size(84.dp),
                    color = Color.Transparent
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = appString(R.string.profile_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(borderWidth)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(borderWidth),
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

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = username,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = email,
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isEmergencyContact) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FriendProfileEmergencyBadge()
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF5E9F7A),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = appString(R.string.friends_profile_loading),
                            color = Color(0xFF5C6A64),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                level != null && points != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appString(R.string.profile_level, safeLevel),
                            color = tierColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = appString(
                                R.string.profile_points,
                                pointsInCurrentLevel.toInt(),
                                pointsNeededForNext.toInt()
                            ),
                            color = Color(0xFF77837D),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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

                level != null -> {
                    Text(
                        text = appString(R.string.profile_level, safeLevel),
                        color = tierColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> {
                    Text(
                        text = appString(R.string.friends_profile_level_unavailable),
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendProfileDetailsCard(
    email: String,
    isEmergencyContact: Boolean,
    level: Long?,
    points: Long?
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0xFFDCE8E0)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = appString(R.string.friends_profile_details_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FriendProfileDetailRow(
                icon = Icons.Default.Email,
                label = appString(R.string.friends_profile_email_label),
                value = email
            )

            FriendProfileDetailRow(
                icon = Icons.Default.Star,
                label = appString(R.string.friends_profile_level_label),
                value = level?.toString() ?: appString(R.string.friends_profile_level_unavailable)
            )

            if (points != null) {
                FriendProfileDetailRow(
                    icon = Icons.Default.CheckCircle,
                    label = appString(R.string.friends_profile_points_label),
                    value = points.toString()
                )
            }

            if (isEmergencyContact) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFEAF5EF),
                    border = BorderStroke(1.dp, Color(0xFFD4E6DA))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = Color(0xFF4F7D66),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = appString(R.string.friends_emergency_contact_title),
                            color = Color(0xFF4F7D66),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendProfileLoadingCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0xFFDCE8E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF5E9F7A),
                strokeWidth = 2.dp
            )
            Text(
                text = appString(R.string.friends_profile_loading),
                color = Color(0xFF5C6A64),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FriendProfileErrorCard(
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0xFFE8DAD4))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = appString(R.string.friends_profile_error_title),
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = appString(R.string.friends_profile_error_description),
                color = Color(0xFF67756F),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5E9F7A),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = appString(R.string.retry_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FriendProfileEmergencyBadge() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEAF5EF),
        border = BorderStroke(1.dp, Color(0xFFD4E6DA))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = Color(0xFF4F7D66),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = appString(R.string.friends_emergency_contact_title),
                color = Color(0xFF4F7D66),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FriendProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FriendProfileDetailIcon(
            icon = icon,
            tint = Color(0xFF5E9F7A)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(0xFF66756F),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color(0xFF23333A),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FriendProfileDetailIcon(
    icon: ImageVector,
    tint: Color,
    size: Dp = 38.dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f)
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
}

@Composable
private fun FriendProfileActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
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
                tint = Color(0xFF33413B)
            )
        }
    }
}
