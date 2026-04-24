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
import java.util.Locale
import kotlin.math.max

private data class RoutePlannerSheetState(
    val selectedPriority: RoutePriority,
    val showProfilePreferences: Boolean,
    val distanceText: String,
    val durationText: String,
    val puntsInteres: List<com.safesteps.data.PuntInteres>
)

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
    showProfilePreferences: Boolean = false
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
                selected = selectedPriority == RoutePriority.PERSONALIZED,
                icon = Icons.Default.Person,
                activeColor = Color(0xFF6A768F),
                onClick = { onPrioritySelected(RoutePriority.PERSONALIZED) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_preferences")
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
    sheetState: RoutePlannerSheetState,
    onPrioritySelected: (RoutePriority) -> Unit,
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
                selectedPriority = sheetState.selectedPriority,
                onPrioritySelected = onPrioritySelected,
                showProfilePreferences = sheetState.showProfilePreferences
            )

            Spacer(modifier = Modifier.height(16.dp))

            PoiSummary(puntsInteres = sheetState.puntsInteres)

            RouteDetails(
                distanceText = sheetState.distanceText,
                durationText = sheetState.durationText
            )

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
private fun NavigationTopBanner(
    destinationText: String,
    activeInstruction: ActiveNavigationInstruction?,
    modifier: Modifier = Modifier
) {
    val navigationActiveLabel = appString(R.string.navigation_active)
    val followRouteLabel = appString(R.string.navigation_follow_route)
    val destinationFallback = appString(R.string.destination_label)
    val resolvedDestination = destinationText.ifBlank { destinationFallback }
    val actionLabel = navigationInstructionActionLabel(
        maneuver = activeInstruction?.maneuver,
        fallbackText = followRouteLabel
    )
    val instructionTitle = navigationInstructionHeadline(
        instruction = activeInstruction,
        actionLabel = actionLabel,
        fallbackText = followRouteLabel
    )
    val instructionSubtitle = navigationInstructionSubtitle(
        instruction = activeInstruction,
        destinationText = resolvedDestination
    )

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
                            text = instructionTitle,
                            color = Color.White,
                            modifier = Modifier.testTag("active_route_instruction"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = instructionSubtitle,
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                }
            }
        }
    }
}

private fun routeOverlayBottomPadding(
    uiState: MapUiState,
    density: androidx.compose.ui.unit.Density,
    sheetHeightPx: Float,
    sheetOffsetPx: Float,
    routeBottomBarHeightPx: Float,
    routeCompletedCardHeightPx: Float
): Dp {
    return when {
        uiState.routeCompleted -> with(density) { routeCompletedCardHeightPx.toDp() } + 18.dp
        uiState.modoRuta -> with(density) { routeBottomBarHeightPx.toDp() } + 18.dp
        uiState.destinoSeleccionado != null -> {
            val currentVisibleHeightPx = sheetHeightPx - sheetOffsetPx
            with(density) { currentVisibleHeightPx.toDp() } + 16.dp
        }

        else -> 16.dp
    }
}

private fun shouldAllowSheetDrag(uiState: MapUiState): Boolean {
    return uiState.destinoSeleccionado != null && !uiState.modoRuta
}

private fun overlayPlannerSheetState(uiState: MapUiState): RoutePlannerSheetState {
    return RoutePlannerSheetState(
        selectedPriority = uiState.prioridadSeleccionada,
        showProfilePreferences = false,
        distanceText = uiState.distanceText,
        durationText = uiState.durationText,
        puntsInteres = uiState.puntsInteres
    )
}

@Composable
private fun BoxScope.NavigationTopBannerOverlay(uiState: MapUiState) {
    AnimatedVisibility(
        visible = uiState.modoRuta && !uiState.routeCompleted,
        enter = slideInVertically(initialOffsetY = { -it / 2 }),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        NavigationTopBanner(
            destinationText = uiState.textoDestino,
            activeInstruction = uiState.activeNavigationInstruction
        )
    }
}

@Composable
private fun BoxScope.RoutePlannerSheetOverlay(
    uiState: MapUiState,
    density: androidx.compose.ui.unit.Density,
    sheetOffsetPx: Float,
    visibleSheetHeightPx: Float,
    sheetHeightPxState: androidx.compose.runtime.MutableFloatState,
    sheetDragState: androidx.compose.foundation.gestures.DraggableState,
    onSheetDragStopped: () -> Unit,
    onSheetOffsetChange: (Float) -> Unit,
    onPrioritySelected: (RoutePriority) -> Unit,
    onClose: () -> Unit,
    onStartRoute: () -> Unit,
    showProfilePreferences: Boolean
) {
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
                        onSheetOffsetChange(maxCollapsedOffset)
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = sheetDragState,
                    onDragStopped = { onSheetDragStopped() }
                ),
            sheetState = overlayPlannerSheetState(uiState).copy(
                showProfilePreferences = showProfilePreferences
            ),
            onPrioritySelected = onPrioritySelected,
            onClose = {
                onSheetOffsetChange(0f)
                onClose()
            },
            onStartRoute = onStartRoute
        )
    }
}

@Composable
private fun BoxScope.RouteActiveBottomBarOverlay(
    uiState: MapUiState,
    routeBottomBarHeightPxState: androidx.compose.runtime.MutableFloatState,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = uiState.modoRuta && !uiState.routeCompleted,
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
            onClose = onClose
        )
    }
}

@Composable
private fun BoxScope.RouteCompletedBottomCardOverlay(
    uiState: MapUiState,
    routeCompletedCardHeightPxState: androidx.compose.runtime.MutableFloatState,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = uiState.routeCompleted && uiState.routeCompletionSummary != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
    ) {
        uiState.routeCompletionSummary?.let { summary ->
            RouteCompletedBottomCard(
                summary = summary,
                onClose = onClose,
                modifier = Modifier.onGloballyPositioned {
                    routeCompletedCardHeightPxState.floatValue = it.size.height.toFloat()
                }
            )
        }
    }
}

@Composable
private fun navigationInstructionActionLabel(
    maneuver: NavigationManeuver?,
    fallbackText: String
): String {
    return when (maneuver) {
        NavigationManeuver.CONTINUE -> appString(R.string.navigation_instruction_continue)
        NavigationManeuver.SLIGHT_LEFT -> appString(R.string.navigation_instruction_slight_left)
        NavigationManeuver.LEFT -> appString(R.string.navigation_instruction_left)
        NavigationManeuver.SHARP_LEFT -> appString(R.string.navigation_instruction_sharp_left)
        NavigationManeuver.SLIGHT_RIGHT -> appString(R.string.navigation_instruction_slight_right)
        NavigationManeuver.RIGHT -> appString(R.string.navigation_instruction_right)
        NavigationManeuver.SHARP_RIGHT -> appString(R.string.navigation_instruction_sharp_right)
        NavigationManeuver.U_TURN -> appString(R.string.navigation_instruction_uturn)
        NavigationManeuver.ARRIVE -> appString(R.string.navigation_instruction_arrive)
        null -> fallbackText
    }
}

@Composable
private fun navigationInstructionHeadline(
    instruction: ActiveNavigationInstruction?,
    actionLabel: String,
    fallbackText: String
): String {
    if (instruction == null) {
        return fallbackText
    }

    if (instruction.maneuver == NavigationManeuver.ARRIVE) {
        return actionLabel
    }

    if (instruction.isCorrective && instruction.maneuver == NavigationManeuver.U_TURN) {
        return appString(R.string.navigation_instruction_now_action, actionLabel)
    }

    val distanceLabel = formatDistanceLabel(instruction.distanceMeters)
    return when {
        instruction.maneuver == NavigationManeuver.CONTINUE && instruction.distanceMeters > 20.0 -> {
            appString(R.string.navigation_instruction_continue_for_distance, distanceLabel)
        }

        instruction.distanceMeters <= 20.0 -> {
            appString(R.string.navigation_instruction_now_action, actionLabel)
        }

        else -> {
            appString(
                R.string.navigation_instruction_in_distance_action,
                distanceLabel,
                actionLabel.replaceFirstChar { char ->
                    char.lowercase(Locale.getDefault())
                }
            )
        }
    }
}

@Composable
private fun navigationInstructionSubtitle(
    instruction: ActiveNavigationInstruction?,
    destinationText: String
): String {
    if (instruction == null) {
        return appString(
            R.string.navigation_instruction_towards_destination,
            compactDestinationText(destinationText)
        )
    }

    val destinationHint = compactDestinationText(destinationText)
    if (instruction.isCorrective) {
        val followUpAction = navigationInstructionActionLabel(
            maneuver = instruction.followUpManeuver,
            fallbackText = appString(R.string.navigation_instruction_continue)
        )
        val followUpDistance = instruction.followUpDistanceMeters?.let(::formatDistanceLabel)
        return if (followUpDistance != null) {
            appString(
                R.string.navigation_instruction_after_uturn_action,
                followUpDistance,
                followUpAction.replaceFirstChar { char ->
                    char.lowercase(Locale.getDefault())
                }
            )
        } else {
            appString(
                R.string.navigation_instruction_towards_destination,
                destinationHint
            )
        }
    }

    return when (instruction.maneuver) {
        NavigationManeuver.ARRIVE -> destinationHint
        else -> appString(
            R.string.navigation_instruction_towards_destination,
            destinationHint
        )
    }
}

private fun compactDestinationText(destinationText: String): String {
    val parts = destinationText
        .split(",")
        .map(String::trim)
        .filter(String::isNotBlank)

    return when {
        parts.isEmpty() -> destinationText
        parts.first().length >= 8 -> parts.first()
        parts.size >= 2 -> "${parts[0]}, ${parts[1]}"
        else -> parts.first()
    }
}

@Composable
private fun NavigationCompactMetric(
    icon: ImageVector,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF6F8FB))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            color = Color(0xFF202124),
            modifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RouteActiveBottomBar(
    durationText: String,
    distanceText: String,
    etaText: String,
    onClose: () -> Unit
) {
    val closeLabel = appString(R.string.close)
    val remainingLabel = appString(R.string.navigation_remaining)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = durationText,
                    modifier = Modifier
                        .semantics { testTag = "active_route_duration" }
                        .testTag("active_route_duration"),
                    color = Color(0xFF202124),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = remainingLabel,
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            NavigationCompactMetric(
                icon = Icons.Default.LocationOn,
                value = distanceText,
                accentColor = Color(0xFF1A73E8),
                valueTestTag = "active_route_distance"
            )

            Spacer(modifier = Modifier.width(8.dp))

            NavigationCompactMetric(
                icon = Icons.Default.AccessTime,
                value = etaText,
                accentColor = Color(0xFF34A853),
                valueTestTag = "active_route_eta"
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFF1F3F4)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
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
    }
}

@Composable
private fun RouteCompletionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF6F8FB))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF5F6368),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color(0xFF202124),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RouteCompletedBottomCard(
    summary: RouteCompletionSummary,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = appString(R.string.navigation_route_completed_title),
                color = Color(0xFF202124),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appString(R.string.navigation_route_completed_message),
                color = Color(0xFF5F6368),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RouteCompletionMetric(
                    label = appString(R.string.navigation_route_summary_distance),
                    value = summary.distanceText,
                    modifier = Modifier.weight(1f)
                )
                RouteCompletionMetric(
                    label = appString(R.string.navigation_route_summary_duration),
                    value = summary.durationText,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { testTag = "btn_exit_route" }
                    .testTag("btn_exit_route"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A73E8),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = appString(R.string.exit_route),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
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
    val routeCompletedCardHeightPxState = remember { mutableFloatStateOf(0f) }

    val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
    val collapsedSheetOffset = max(0f, sheetHeightPxState.floatValue - visibleSheetHeightPx)

    val bottomPadding by animateDpAsState(
        targetValue = routeOverlayBottomPadding(
            uiState = uiState,
            density = density,
            sheetHeightPx = sheetHeightPxState.floatValue,
            sheetOffsetPx = sheetOffsetPx,
            routeBottomBarHeightPx = routeBottomBarHeightPxState.floatValue,
            routeCompletedCardHeightPx = routeCompletedCardHeightPxState.floatValue
        ),
        label = "routeOverlayBottomPadding"
    )

    val sheetDragState = rememberDraggableState { delta ->
        if (shouldAllowSheetDrag(uiState)) {
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

    NavigationTopBannerOverlay(uiState = uiState)
    RoutePlannerSheetOverlay(
        uiState = uiState,
        density = density,
        sheetOffsetPx = sheetOffsetPx,
        visibleSheetHeightPx = visibleSheetHeightPx,
        sheetHeightPxState = sheetHeightPxState,
        sheetDragState = sheetDragState,
        onSheetDragStopped = {
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
        },
        onSheetOffsetChange = { sheetOffsetPx = it },
        onPrioritySelected = onPrioritySelected,
        onClose = onClose,
        onStartRoute = onStartRoute,
        showProfilePreferences = showProfilePreferences
    )
    RouteActiveBottomBarOverlay(
        uiState = uiState,
        routeBottomBarHeightPxState = routeBottomBarHeightPxState,
        onClose = onClose
    )
    RouteCompletedBottomCardOverlay(
        uiState = uiState,
        routeCompletedCardHeightPxState = routeCompletedCardHeightPxState,
        onClose = onClose
    )
}
