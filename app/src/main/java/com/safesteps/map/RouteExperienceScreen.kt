package com.safesteps.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.safesteps.R
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.appPlural
import com.safesteps.i18n.appString
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
private fun RoutePriorityCompactOption(
    title: String,
    selected: Boolean,
    icon: ImageVector,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) activeColor.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (selected) activeColor else Color(0xFF77837D)
    val borderColor = if (selected) activeColor else Color(0xFFE7ECE8)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun RoutePlannerHeader(onClose: () -> Unit) {
    val closeLabel = appString(R.string.close)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = appString(R.string.route_priorities),
            color = Color(0xFF1F2C3B),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )

        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = Color(0xFFEAF9EF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = Color(0xFF5CCF8A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = closeLabel,
                tint = Color(0xFF6C7772)
            )
        }
    }
}

@Composable
private fun RoutePrioritySelector(
    selectedPriority: RoutePriority,
    onPrioritySelected: (RoutePriority) -> Unit,
    showProfilePreferences: Boolean = false,
    onProfilePreferencesClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoutePriorityCompactOption(
                title = appString(R.string.filter_safety),
                selected = selectedPriority == RoutePriority.SAFETY,
                icon = Icons.Default.Security,
                activeColor = Color(0xFF1F4A85),
                onClick = { onPrioritySelected(RoutePriority.SAFETY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_safety")
            )

            RoutePriorityCompactOption(
                title = appString(R.string.filter_comfort),
                selected = selectedPriority == RoutePriority.ACCESSIBILITY,
                icon = Icons.AutoMirrored.Filled.Accessible,
                activeColor = Color(0xFF7FD7AA),
                onClick = { onPrioritySelected(RoutePriority.ACCESSIBILITY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_accessibility")
            )

            RoutePriorityCompactOption(
                title = appString(R.string.filter_climate),
                selected = selectedPriority == RoutePriority.HEAT,
                icon = Icons.Default.WbSunny,
                activeColor = Color(0xFFFF7B42),
                onClick = { onPrioritySelected(RoutePriority.HEAT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_clima")
            )
        }

        if (showProfilePreferences) {
            RoutePriorityCompactOption(
                title = appString(R.string.filter_preferences),
                selected = false,
                icon = Icons.Default.Person,
                activeColor = Color(0xFF6A768F),
                onClick = onProfilePreferencesClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PoiSummary(puntsInteres: List<com.safesteps.data.PuntInteres>) {
    if (puntsInteres.isEmpty()) return

    val fonts = puntsInteres.count { it.tipus.uppercase() == "FONT" }
    val bancs = puntsInteres.count { it.tipus.uppercase() == "BANC" }
    val comisaries = puntsInteres.count { it.tipus.uppercase() == "COMISSARIA" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F4F3), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics{ testTag = "btn_places" }
            .testTag("btn_places"),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (fonts > 0) {
            Text(
                text = "\uD83D\uDCA7 ${appPlural(R.plurals.poi_fountains, fonts, fonts)}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4A45)
            )
        }
        if (bancs > 0) {
            Text(
                text = "\uD83E\uDE91 ${appPlural(R.plurals.poi_benches, bancs, bancs)}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4A45)
            )
        }
        if (comisaries > 0) {
            Text(
                text = "\uD83D\uDC6E ${appPlural(R.plurals.poi_police_stations, comisaries, comisaries)}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4A45)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun RouteDetails(distanceText: String, durationText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFF5E6763),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = distanceText,
            color = Color(0xFF5E6763),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "\u2022",
            color = Color(0xFF88D1A6),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = Color(0xFF5E6763),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = durationText,
            color = Color(0xFF5E6763),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RoutePlannerSheet(
    modifier: Modifier = Modifier,
    selectedPriority: RoutePriority,
    onPrioritySelected: (RoutePriority) -> Unit,
    showProfilePreferences: Boolean = false,
    distanceText: String,
    durationText: String,
    puntsInteres: List<com.safesteps.data.PuntInteres>,
    onClose: () -> Unit,
    onStartRoute: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFD8DDDA))
            )

            Spacer(modifier = Modifier.height(14.dp))

            RoutePlannerHeader(onClose = onClose)

            Spacer(modifier = Modifier.height(12.dp))

            RoutePrioritySelector(
                selectedPriority = selectedPriority,
                onPrioritySelected = onPrioritySelected,
                showProfilePreferences = showProfilePreferences
            )

            Spacer(modifier = Modifier.height(16.dp))

            PoiSummary(puntsInteres = puntsInteres)

            RouteDetails(distanceText = distanceText, durationText = durationText)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "btn_start_route" }
                    .testTag("btn_start_route")
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC86A37),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = appString(R.string.start_route),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun NavigationStatusChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NavigationTopBanner(
    destinationText: String,
    distanceText: String,
    etaText: String,
    modifier: Modifier = Modifier
) {
    val navigationActiveLabel = appString(R.string.navigation_active)
    val followRouteLabel = appString(R.string.navigation_follow_route)
    val walkingModeLabel = appString(R.string.navigation_walk_mode)
    val destinationFallback = appString(R.string.destination_label)
    val resolvedDestination = destinationText.ifBlank { destinationFallback }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A73E8),
                            Color(0xFF4A8DF7)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = navigationActiveLabel,
                            color = Color.White.copy(alpha = 0.82f),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = followRouteLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resolvedDestination,
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = etaText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NavigationStatusChip(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        text = walkingModeLabel,
                        modifier = Modifier.weight(1f)
                    )
                    NavigationStatusChip(
                        icon = Icons.Default.LocationOn,
                        text = distanceText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationInfoTile(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF6F8FB))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(14.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(0xFF70757A),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color(0xFF202124),
                modifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RouteActiveBottomBar(
    durationText: String,
    distanceText: String,
    etaText: String,
    destinationText: String,
    onClose: () -> Unit
) {
    val closeLabel = appString(R.string.close)
    val remainingLabel = appString(R.string.navigation_remaining)
    val distanceLabel = appString(R.string.navigation_distance)
    val arrivalShortLabel = appString(R.string.navigation_arrival_short)
    val destinationLabel = appString(R.string.destination_label)
    val resolvedDestination = destinationText.ifBlank { destinationLabel }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = durationText,
                        modifier = Modifier
                            .semantics { testTag = "active_route_duration" }
                            .testTag("active_route_duration"),
                        color = Color(0xFF202124),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = remainingLabel,
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = Color(0xFFF1F3F4)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(46.dp)
                            .semantics { testTag = "btn_close_route" }
                            .testTag("btn_close_route")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = closeLabel,
                            tint = Color(0xFF3D4A45)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationInfoTile(
                    icon = Icons.Default.LocationOn,
                    label = distanceLabel,
                    value = distanceText,
                    accentColor = Color(0xFF1A73E8),
                    modifier = Modifier
                        .weight(1f),
                    valueTestTag = "active_route_distance"
                )
                NavigationInfoTile(
                    icon = Icons.Default.AccessTime,
                    label = arrivalShortLabel,
                    value = etaText,
                    accentColor = Color(0xFF34A853),
                    modifier = Modifier
                        .weight(1f),
                    valueTestTag = "active_route_eta"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF8FAFD))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE8F0FE)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destinationLabel,
                        color = Color(0xFF70757A),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = resolvedDestination,
                        color = Color(0xFF202124),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.RouteExperienceOverlay(
    uiState: MapUiState,
    showProfilePreferences: Boolean,
    onPrioritySelected: (RoutePriority) -> Unit,
    onStartRoute: () -> Unit,
    onClose: () -> Unit,
    onBottomPaddingChange: (Dp) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val sheetHeightPxState = remember { mutableFloatStateOf(0f) }
    var sheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val routeBottomBarHeightPxState = remember { mutableFloatStateOf(0f) }

    val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
    val collapsedSheetOffset = max(0f, sheetHeightPxState.floatValue - visibleSheetHeightPx)

    val bottomPadding by animateDpAsState(
        targetValue = when {
            uiState.modoRuta -> with(density) { routeBottomBarHeightPxState.floatValue.toDp() } + 18.dp
            uiState.destinoSeleccionado != null -> {
                val currentVisibleHeightPx = sheetHeightPxState.floatValue - sheetOffsetPx
                val currentVisibleHeightDp = with(density) { currentVisibleHeightPx.toDp() }
                currentVisibleHeightDp + 16.dp
            }
            else -> 16.dp
        },
        label = "routeOverlayBottomPadding"
    )

    val sheetDragState = rememberDraggableState { delta ->
        if (uiState.destinoSeleccionado != null && !uiState.modoRuta) {
            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
        }
    }

    LaunchedEffect(bottomPadding) {
        onBottomPaddingChange(bottomPadding)
    }

    LaunchedEffect(uiState.destinoSeleccionado, uiState.modoRuta) {
        if (uiState.destinoSeleccionado == null || uiState.modoRuta) {
            sheetOffsetPx = 0f
        }
    }

    AnimatedVisibility(
        visible = uiState.modoRuta,
        enter = slideInVertically(initialOffsetY = { -it / 2 }),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        NavigationTopBanner(
            destinationText = uiState.textoDestino,
            distanceText = uiState.distanceText,
            etaText = uiState.etaText
        )
    }

    AnimatedVisibility(
        visible = uiState.destinoSeleccionado != null && !uiState.modoRuta,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 10.dp)
    ) {
        RoutePlannerSheet(
            modifier = Modifier
                .offset(y = with(density) { sheetOffsetPx.toDp() })
                .onGloballyPositioned {
                    val newSheetHeightPx = it.size.height.toFloat()
                    sheetHeightPxState.floatValue = newSheetHeightPx
                    val maxCollapsedOffset = max(0f, newSheetHeightPx - visibleSheetHeightPx)
                    if (sheetOffsetPx > maxCollapsedOffset) {
                        sheetOffsetPx = maxCollapsedOffset
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = sheetDragState,
                    onDragStopped = {
                        val target = if (sheetOffsetPx > collapsedSheetOffset / 2f) {
                            collapsedSheetOffset
                        } else {
                            0f
                        }
                        coroutineScope.launch {
                            animate(initialValue = sheetOffsetPx, targetValue = target) { value, _ ->
                                sheetOffsetPx = value
                            }
                        }
                    }
                ),
            selectedPriority = uiState.prioridadSeleccionada,
            onPrioritySelected = onPrioritySelected,
            showProfilePreferences = showProfilePreferences,
            distanceText = uiState.distanceText,
            durationText = uiState.durationText,
            onClose = {
                sheetOffsetPx = 0f
                onClose()
            },
            puntsInteres = uiState.puntsInteres,
            onStartRoute = onStartRoute
        )
    }

    AnimatedVisibility(
        visible = uiState.modoRuta,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onGloballyPositioned {
                routeBottomBarHeightPxState.floatValue = it.size.height.toFloat()
            }
    ) {
        RouteActiveBottomBar(
            durationText = uiState.durationText,
            distanceText = uiState.distanceText,
            etaText = uiState.etaText,
            destinationText = uiState.textoDestino,
            onClose = onClose
        )
    }
}
