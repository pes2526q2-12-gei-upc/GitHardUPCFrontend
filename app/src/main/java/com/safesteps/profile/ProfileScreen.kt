package com.safesteps.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.LanguageSelector
import com.safesteps.i18n.appPlural
import com.safesteps.i18n.appString
import kotlin.math.roundToInt

private val filterLevelResIds = listOf(
    R.string.filter_level_low,
    R.string.filter_level_medium,
    R.string.filter_level_high,
    R.string.filter_level_required
)

private data class ProfileFilterGroup(
    val titleResId: Int,
    val filterResIds: List<Int>
)

private val profileFilterGroups = listOf(
    ProfileFilterGroup(
        titleResId = R.string.filter_safety,
        filterResIds = listOf(
            R.string.profile_filter_security_cameras,
            R.string.profile_filter_police_stations,
            R.string.profile_filter_criminal_incidents,
            R.string.profile_filter_user_reported_incidents
        )
    ),
    ProfileFilterGroup(
        titleResId = R.string.filter_comfort,
        filterResIds = listOf(
            R.string.profile_filter_benches,
            R.string.profile_filter_noise_pollution,
            R.string.profile_filter_escalators,
            R.string.profile_filter_drinking_fountains
        )
    ),
    ProfileFilterGroup(
        titleResId = R.string.filter_climate,
        filterResIds = listOf(
            R.string.profile_filter_trees,
            R.string.profile_filter_air_quality,
            R.string.profile_filter_climate_shelters
        )
    )
)

@Composable
fun ProfileScreen(
    user: UserInfo,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCustomizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFilterCount = profileFilterGroups.sumOf { it.filterResIds.size }

    var expandedGroups by rememberSaveable {
        mutableStateOf(List(profileFilterGroups.size) { false })
    }
    var showDeleteBanner by rememberSaveable { mutableStateOf(false) }
    var filterValues by rememberSaveable {
        mutableStateOf(List(totalFilterCount) { 1 })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
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

        Spacer(modifier = Modifier.height(24.dp))

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
                ProfileHeader(user = user)

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(28.dp))

                var filterOffset = 0
                profileFilterGroups.forEachIndexed { groupIndex, group ->
                    val groupStartIndex = filterOffset

                    FilterAccordion(
                        title = appString(group.titleResId),
                        subtitle = appPlural(
                            R.plurals.profile_filter_count,
                            group.filterResIds.size,
                            group.filterResIds.size
                        ),
                        expanded = expandedGroups[groupIndex],
                        filterLabels = group.filterResIds.map { appString(it) },
                        filterValues = group.filterResIds.indices.map { localIndex ->
                            filterValues[groupStartIndex + localIndex]
                        },
                        onExpandedChange = {
                            expandedGroups = expandedGroups.toMutableList().also { groups ->
                                groups[groupIndex] = !groups[groupIndex]
                            }
                        },
                        onValueChange = { localIndex, newValue ->
                            filterValues = filterValues.toMutableList().also { values ->
                                values[groupStartIndex + localIndex] = newValue
                            }
                        }
                    )

                    filterOffset += group.filterResIds.size

                    if (groupIndex < profileFilterGroups.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            showDeleteBanner = false
                            onLogout()
                        },
                        modifier = Modifier
                            .weight(1.35f)
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

                    Button(
                        onClick = { showDeleteBanner = true },
                        modifier = Modifier
                            .weight(0.95f)
                            .height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF8E2D8),
                            contentColor = Color(0xFF8F3D1B)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = appString(R.string.delete_account_short),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                if (showDeleteBanner) {
                    Spacer(modifier = Modifier.height(16.dp))

                    DeleteAccountBanner(
                        onDismiss = { showDeleteBanner = false },
                        onConfirm = {
                            showDeleteBanner = false
                            onDeleteAccount()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: UserInfo) {
    // Hardcoded points for testing/presentation
    val points = 1045
    val pointsPerLevel = 100
    val maxLevel = 20

    val currentLevel = (points / pointsPerLevel + 1).coerceAtMost(maxLevel)
    val pointsInCurrentLevel = points % pointsPerLevel
    val progress = if (currentLevel == maxLevel) 1f else pointsInCurrentLevel.toFloat() / pointsPerLevel

    // The style changes every 5 levels
    val tier = ((currentLevel - 1) / 5).coerceIn(0, 3)
    val tierColor = when (tier) {
        0 -> Color(0xFFB0BEC5) // Bronze/Silver
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFF00E676) // Emerald
        else -> Color(0xFFE040FB) // Diamond
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

        Column(
            modifier = Modifier.weight(1f)
        ) {
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
                    text = appString(R.string.profile_points, pointsInCurrentLevel, pointsPerLevel),
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
private fun FilterAccordion(
    title: String,
    subtitle: String,
    expanded: Boolean,
    filterLabels: List<String>,
    filterValues: List<Int>,
    onExpandedChange: () -> Unit,
    onValueChange: (Int, Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE5ECE7), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF9FBFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandedChange),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C6A64)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                filterLabels.forEachIndexed { index, label ->
                    FilterPreferenceControl(
                        title = label,
                        value = filterValues[index],
                        onValueChange = { onValueChange(index, it) }
                    )

                    if (index < filterLabels.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPreferenceControl(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val selectedLabel = appString(filterLevelResIds[value])

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = Color(0xFF23333A),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = selectedLabel,
            color = Color(0xFF5C6A64),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 3)) },
        valueRange = 0f..3f,
        steps = 2,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        filterLevelResIds.forEachIndexed { index, labelResId ->
            Text(
                modifier = Modifier.weight(1f),
                text = appString(labelResId),
                color = if (index == value) Color(0xFF23333A) else Color(0xFF8A948F),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (index == value) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    filterLevelResIds.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                maxLines = 2
            )
        }
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
