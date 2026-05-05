package com.safesteps.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safesteps.R
import com.safesteps.i18n.appPlural
import com.safesteps.i18n.appString
import kotlin.math.roundToInt

@Composable
fun RouteFiltersScreen(
    filterState: ProfileFilterState,
    onFilterValueChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        RouteFiltersTopBar(
            onBack = onBack,
            isSyncing = filterState.isLoading || filterState.isSaving
        )

        Spacer(modifier = Modifier.height(20.dp))

        RouteFiltersGroups(
            filterValues = filterState.values,
            areFiltersEnabled = filterState.areEnabled,
            onFilterValueChange = onFilterValueChange
        )
    }
}

@Composable
private fun RouteFiltersTopBar(
    onBack: () -> Unit,
    isSyncing: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(40.dp),
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
                    text = appString(R.string.route_filters_title),
                    color = Color(0xFF42514B),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                RouteFiltersSyncChip(isSyncing = isSyncing)
            }
        }
    }
}

@Composable
private fun RouteFiltersSyncChip(
    isSyncing: Boolean
) {
    val backgroundColor = if (isSyncing) Color(0xFFFFF1C9) else Color(0xFFE7F6EC)
    val borderColor = if (isSyncing) Color(0xFFE8C24D) else Color(0xFFA4D5B4)
    val contentColor = if (isSyncing) Color(0xFFAF7B00) else Color(0xFF2F8A57)
    val transition = rememberInfiniteTransition(label = "routeFiltersSync")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "routeFiltersSyncRotation"
    )

    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = if (isSyncing) {
                    appString(R.string.route_filters_sync_loading)
                } else {
                    appString(R.string.route_filters_sync_ready)
                },
                tint = contentColor,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer { rotationZ = if (isSyncing) rotation else 0f }
            )
        }
    }
}

@Composable
private fun RouteFiltersGroups(
    filterValues: List<Int>,
    areFiltersEnabled: Boolean,
    onFilterValueChange: (Int, Int) -> Unit
) {
    var filterOffset = 0
    var expandedGroupIndex by rememberSaveable { mutableIntStateOf(-1) }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        profileFilterGroups.forEachIndexed { groupIndex, group ->
            val groupStartIndex = filterOffset

            RouteFilterGroupCard(
                title = appString(group.titleResId),
                description = appString(group.descriptionResId),
                icon = group.icon,
                accentColor = group.accentColor,
                filterCountText = appPlural(
                    R.plurals.profile_filter_count,
                    group.filters.size,
                    group.filters.size
                ),
                filters = group.filters.mapIndexed { localIndex, filter ->
                    RouteFilterItemUi(
                        index = groupStartIndex + localIndex,
                        title = appString(filter.labelResId),
                        value = filterValues.getOrElse(groupStartIndex + localIndex) { 1 }
                    )
                },
                expanded = expandedGroupIndex == groupIndex,
                enabled = areFiltersEnabled,
                onExpandedChange = {
                    expandedGroupIndex = if (expandedGroupIndex == groupIndex) -1 else groupIndex
                },
                onValueChange = onFilterValueChange
            )

            filterOffset += group.filters.size
        }
    }
}

private data class RouteFilterItemUi(
    val index: Int,
    val title: String,
    val value: Int
)

@Composable
private fun RouteFilterGroupCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    filterCountText: String,
    filters: List<RouteFilterItemUi>,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: () -> Unit,
    onValueChange: (Int, Int) -> Unit
) {
    val cardBackground = if (expanded) Color(0xFFFDFEFD) else Color.White
    val cardBorderColor = accentColor.copy(alpha = if (expanded) 0.18f else 0.10f)
    val headerBackground = accentColor.copy(alpha = if (expanded) 0.12f else 0.08f)
    val headerBorderColor = accentColor.copy(alpha = if (expanded) 0.16f else 0.10f)
    val arrowBackground = if (expanded) accentColor.copy(alpha = 0.14f) else Color(0xFFF1F5F2)
    val arrowTint = if (expanded) accentColor else Color(0xFF65746E)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = cardBackground,
        shadowElevation = if (expanded) 10.dp else 6.dp,
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = headerBackground,
                border = BorderStroke(1.dp, headerBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpandedChange)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.08f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color(0xFF23333A),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = description,
                            color = Color(0xFF63726B),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.10f))
                    ) {
                        Text(
                            text = filterCountText,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = accentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = arrowBackground
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = arrowTint
                            )
                        }
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(accentColor.copy(alpha = 0.10f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    filters.forEach { filter ->
                        RouteFilterControl(
                            title = filter.title,
                            value = filter.value,
                            accentColor = accentColor,
                            enabled = enabled,
                            onValueChange = { onValueChange(filter.index, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteFilterControl(
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
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = selectedLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = selectedDescription,
                color = Color(0xFF5E6F68),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

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
