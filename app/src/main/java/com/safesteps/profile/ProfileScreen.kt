package com.safesteps.profile

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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private val filterLevelDescriptionResIds = listOf(
    R.string.filter_level_low_description,
    R.string.filter_level_medium_description,
    R.string.filter_level_high_description,
    R.string.filter_level_required_description
)

private data class ProfileFilterGroup(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
    val accentColor: Color,
    val filterResIds: List<Int>
)

private val profileFilterGroups = listOf(
    ProfileFilterGroup(
        titleResId = R.string.filter_safety,
        descriptionResId = R.string.profile_filter_group_safety_description,
        icon = Icons.Default.Security,
        accentColor = Color(0xFF2A5F8A),
        filterResIds = listOf(
            R.string.profile_filter_security_cameras,
            R.string.profile_filter_police_stations,
            R.string.profile_filter_criminal_incidents,
            R.string.profile_filter_user_reported_incidents
        )
    ),
    ProfileFilterGroup(
        titleResId = R.string.filter_comfort,
        descriptionResId = R.string.profile_filter_group_comfort_description,
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        accentColor = Color(0xFF388E6C),
        filterResIds = listOf(
            R.string.profile_filter_benches,
            R.string.profile_filter_noise_pollution,
            R.string.profile_filter_escalators,
            R.string.profile_filter_drinking_fountains
        )
    ),
    ProfileFilterGroup(
        titleResId = R.string.filter_climate,
        descriptionResId = R.string.profile_filter_group_climate_description,
        icon = Icons.Default.WbSunny,
        accentColor = Color(0xFFD97832),
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
    filterValues: List<Int>,
    onFilterValueChange: (Int, Int) -> Unit,
    isLoadingFilters: Boolean,
    areFiltersEnabled: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedGroups by rememberSaveable {
        mutableStateOf(List(profileFilterGroups.size) { false })
    }
    var showDeleteBanner by rememberSaveable { mutableStateOf(false) }

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

                Spacer(modifier = Modifier.height(20.dp))

                ProfileFiltersSection(
                    filterValues = filterValues,
                    expandedGroups = expandedGroups,
                    isLoadingFilters = isLoadingFilters,
                    areFiltersEnabled = areFiltersEnabled,
                    onExpandedGroupToggle = { groupIndex ->
                        expandedGroups = expandedGroups.toMutableList().also { groups ->
                            groups[groupIndex] = !groups[groupIndex]
                        }
                    },
                    onFilterValueChange = onFilterValueChange
                )

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
private fun ProfileFiltersSection(
    filterValues: List<Int>,
    expandedGroups: List<Boolean>,
    isLoadingFilters: Boolean,
    areFiltersEnabled: Boolean,
    onExpandedGroupToggle: (Int) -> Unit,
    onFilterValueChange: (Int, Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD7E5DC), RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFF2F7F3)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFF5E9F7A))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Text(
                    text = appString(R.string.profile_filters_intro_title),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = appString(R.string.profile_filters_intro_message),
                    color = Color(0xFF60716A),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isLoadingFilters) {
                    Spacer(modifier = Modifier.height(12.dp))

                    FiltersSyncStatusRow(
                        isLoadingFilters = isLoadingFilters
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var filterOffset = 0
                profileFilterGroups.forEachIndexed { groupIndex, group ->
                    val groupStartIndex = filterOffset

                    FilterAccordion(
                        title = appString(group.titleResId),
                        description = appString(group.descriptionResId),
                        accentColor = group.accentColor,
                        icon = group.icon,
                        filterCountText = appPlural(
                            R.plurals.profile_filter_count,
                            group.filterResIds.size,
                            group.filterResIds.size
                        ),
                        expanded = expandedGroups[groupIndex],
                        filterLabels = group.filterResIds.map { appString(it) },
                        filterValues = group.filterResIds.indices.map { localIndex ->
                            filterValues.getOrElse(groupStartIndex + localIndex) { 1 }
                        },
                        enabled = areFiltersEnabled,
                        onExpandedChange = { onExpandedGroupToggle(groupIndex) },
                        onValueChange = { localIndex, newValue ->
                            onFilterValueChange(groupStartIndex + localIndex, newValue)
                        }
                    )

                    filterOffset += group.filterResIds.size

                    if (groupIndex < profileFilterGroups.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: UserInfo) {
    if (!user.photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = appString(R.string.profile_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = Color(0xFF6DD29A)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = user.username,
        color = Color(0xFF23333A),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = user.email,
        color = Color(0xFF77837D),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun FiltersSyncStatusRow(
    isLoadingFilters: Boolean
) {
    if (!isLoadingFilters) {
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFE4F0FA)
        ) {
            Text(
                text = appString(R.string.profile_filters_sync_loading),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color(0xFF2A5F8A),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FilterAccordion(
    title: String,
    description: String,
    accentColor: Color,
    icon: ImageVector,
    filterCountText: String,
    expanded: Boolean,
    filterLabels: List<String>,
    filterValues: List<Int>,
    enabled: Boolean,
    onExpandedChange: () -> Unit,
    onValueChange: (Int, Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE3EBE6), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
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
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = filterCountText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = accentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF5C6A64)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(14.dp))

                filterLabels.forEachIndexed { index, label ->
                    FilterPreferenceControl(
                        title = label,
                        value = filterValues.getOrElse(index) { 1 },
                        accentColor = accentColor,
                        enabled = enabled,
                        onValueChange = { onValueChange(index, it) }
                    )

                    if (index < filterLabels.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
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
    accentColor: Color,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val safeValue = value.coerceIn(0, filterLevelResIds.lastIndex)
    val selectedLabel = appString(filterLevelResIds[safeValue])
    val selectedDescription = appString(filterLevelDescriptionResIds[safeValue])

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
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

                Spacer(modifier = Modifier.width(10.dp))

                PreferenceBadge(
                    label = selectedLabel,
                    accentColor = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = selectedDescription,
                color = Color(0xFF6D7B75),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
                value = safeValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 3)) },
                valueRange = 0f..3f,
                steps = 2,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    activeTickColor = accentColor,
                    inactiveTrackColor = Color(0xFFE3E9E6),
                    inactiveTickColor = Color(0xFFC8D3CD)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                filterLevelResIds.forEachIndexed { index, labelResId ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = appString(labelResId),
                        color = if (index == safeValue) accentColor else Color(0xFF8A948F),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == safeValue) FontWeight.SemiBold else FontWeight.Normal,
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
    }
}

@Composable
private fun PreferenceBadge(
    label: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = accentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
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
