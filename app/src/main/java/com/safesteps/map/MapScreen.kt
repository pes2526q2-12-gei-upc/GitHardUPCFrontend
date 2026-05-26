package com.safesteps.map

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.Coord
import com.safesteps.data.Feature
import com.safesteps.data.IssueApiType
import com.safesteps.data.IssueResponseDTO
import com.safesteps.data.PuntInteres
import com.safesteps.data.RouteCompletionResponse
import com.safesteps.data.alternarEstadoEmergenciaUsuario
import com.safesteps.data.cargarContactosEmergenciaUsuario
import com.safesteps.data.esEsdeveniment
import com.safesteps.data.nomMostrat
import com.safesteps.data.obtenerEstadoEmergenciaUsuario
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.appString
import com.safesteps.notifications.BackendWebSocketManager
import com.safesteps.notifications.persistEmergencyNotificationUser
import com.safesteps.notifications.syncCurrentFcmTokenForUser
import org.maplibre.android.annotations.IconFactory
import com.safesteps.ui.notifications.ScreenNotificationManager
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val EmergencyStatusNotificationDurationMillis = 6_000L
private const val EmergencyLocationBroadcastIntervalMillis = 60_000L

private data class MapScreenStrings(
    val mapNotificationTitle: String,
    val permissionNotificationTitle: String,
    val locationPermissionRequiredMessage: String,
    val waitingGpsLocationMessage: String,
    val searchingGpsSignalMessage: String,
    val originLabel: String,
    val destinationLabel: String,
    val hideExtraInfoLabel: String,
    val showExtraInfoLabel: String,
    val standardMapStyleLabel: String,
    val satelliteMapStyleLabel: String,
    val myLocationLabel: String,
    val calculatingBestRouteLabel: String,
    val reportIssueLabel: String,
    val emergencyActionLabel: String,
    val emergencyModeActivatedMessage: String,
    val emergencyModeDeactivatedMessage: String,
    val emergencyModeUpdateFailedMessage: String,
    val emergencyContactHelpTitle: String,
    val emergencyContactHelpRouteAction: String,
    val emergencyContactMarkerLabel: String,
    val emergencyContactDismissLabel: String,
    val reportIssueOutsideBarcelonaMessage: String,
    val issueLoadingAddressLabel: String,
    val issueLocationFallbackLabel: String,
    val mapLocationSelectionRestrictedMessage: String
)

private data class FloatingActionsLayout(
    val compactMode: Boolean,
    val showPoiAction: Boolean,
    val showMapStyleAction: Boolean,
    val showMyLocationAction: Boolean,
    val showReportIssueAction: Boolean,
    val showEmergencyAction: Boolean
)

private data class MapScreenEffectCallbacks(
    val requestLocationPermissions: () -> Unit,
    val onNavigationHeadingChanged: (Float?) -> Unit
)

private data class MapRenderContext(
    val mapView: MapView,
    val context: Context,
    val viewModel: MapViewModel,
    val navigationHeadingDegrees: Float?,
    val originLabel: String,
    val destinationLabel: String,
    val emergencyContactMarkerLabel: String
)

private data class NavigationTrackingState(
    val locationGranted: Boolean,
    val mapaListo: Boolean,
    val modoRuta: Boolean,
    val routeCompleted: Boolean,
    val navigationCameraFollowing: Boolean,
    val currentLocation: Location?
)

private data class RouteExitCameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val tilt: Double,
    val bearing: Double
)

private data class MapScreenActions(
    val requestLocationPermissions: () -> Unit,
    val resetToMainMenu: () -> Unit,
    val onPrioritySelected: (RoutePriority) -> Unit,
    val onAddressSelected: (Feature) -> Unit,
    val onCenterCurrentLocation: () -> Unit,
    val clearRouteExitCameraSnapshot: () -> Unit
)

@Composable
private fun rememberMapScreenActions(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    map: MapLibreMap?,
    context: Context,
    strings: MapScreenStrings,
    navigationHeadingDegrees: Float?,
    routeExitCameraSnapshot: RouteExitCameraSnapshot?,
    onRouteExitCameraSnapshotCaptured: (RouteExitCameraSnapshot?) -> Unit,
    onRouteExitCameraSnapshotConsumed: () -> Unit
): MapScreenActions {
    val requestLocationPermissions = rememberLocationPermissionRequester(
        context = context,
        viewModel = viewModel,
        permissionNotificationTitle = strings.permissionNotificationTitle,
        locationPermissionRequiredMessage = strings.locationPermissionRequiredMessage
    )
    val resetToMainMenu = {
        resetMapToMainMenu(
            viewModel = viewModel,
            mapView = mapView,
            currentLocation = uiState.ultimaUbicacion,
            routeExitCameraSnapshot = routeExitCameraSnapshot
        )
        onRouteExitCameraSnapshotConsumed()
    }
    val onPrioritySelected: (RoutePriority) -> Unit = { priority ->
        processPrioritySelection(
            priority = priority,
            uiState = uiState,
            viewModel = viewModel,
            notificationTitle = strings.mapNotificationTitle,
            waitingGpsLocationMessage = strings.waitingGpsLocationMessage
        )
    }
    val onAddressSelected: (Feature) -> Unit = { feature ->
        processAddressSelection(
            feature = feature,
            uiState = uiState,
            viewModel = viewModel,
            mapView = mapView
        )
    }
    val onCenterCurrentLocation = {
        recenterOnCurrentLocation(
            uiState = uiState,
            viewModel = viewModel,
            mapView = mapView,
            map = map,
            requestLocationPermissions = requestLocationPermissions,
            navigationHeadingDegrees = navigationHeadingDegrees,
            notificationTitle = strings.mapNotificationTitle,
            searchingGpsSignalMessage = strings.searchingGpsSignalMessage,
            onRouteExitCameraSnapshotCaptured = onRouteExitCameraSnapshotCaptured
        )
    }
    return MapScreenActions(
        requestLocationPermissions = requestLocationPermissions,
        resetToMainMenu = resetToMainMenu,
        onPrioritySelected = onPrioritySelected,
        onAddressSelected = onAddressSelected,
        onCenterCurrentLocation = onCenterCurrentLocation,
        clearRouteExitCameraSnapshot = { onRouteExitCameraSnapshotCaptured(null) }
    )
}

@Composable
private fun MapScreenDialogs(
    uiState: MapUiState,
    viewModel: MapViewModel,
    currentUser: UserInfo?,
    strings: MapScreenStrings,
    context: Context,
    onLoginClick: () -> Unit,
    onVoted: () -> Unit = {}
){
    val textMevaUbicacio = appString(R.string.my_location)
    val textExit = context.getString(R.string.report_registered)

    ReportIssueDialog(
        visible = uiState.mostrarIncidencies,
        isLoggedIn = currentUser != null,
        defaultLocationText = textMevaUbicacio,
        onDismiss = {
            viewModel.toggleMenuIncidencies(false)
        },
        onConfirm = { tipus, ubicacio, descripcio, _ ->
            handleNewIssueConfirm(
                tipus = tipus,
                ubicacio = ubicacio,
                descripcio = descripcio,
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                textMevaUbicacio = textMevaUbicacio,
                textExit = textExit,
                outsideBarcelonaMessage = strings.reportIssueOutsideBarcelonaMessage
            )
        },
        onNavigateToLogin = onLoginClick,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
        initialCoord = Coord(0.0, 0.0),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(horizontal = 16.dp)
    )

    uiState.incidenciaSeleccionada?.let { incidencia ->
        val isOwner = currentUser?.username == incidencia.authorName
        val miVot = uiState.userVotes[incidencia.id]
        showIssueDialog(
            incidencia = incidencia,
            isOwner = isOwner,
            isLoggedIn = currentUser != null,
            miVot = miVot,
            onDismiss = { viewModel.selectIssue(null) },
            onConfirmar = { currentUser?.googleId?.let { viewModel.voteIssue(incidencia.id, true, it) }; onVoted() },
            onRebutjar = { currentUser?.googleId?.let { viewModel.voteIssue(incidencia.id, false, it) }; onVoted() },
            onEditar = { viewModel.iniciarEdicio(incidencia) },
            onEsborrar = { viewModel.esborrarIncidencia(incidencia.id) },
            onDesferVot = { currentUser?.googleId?.let { viewModel.desferVot(incidencia.id, it) } }
        )
    }

    uiState.incidenciaEnEdicio?.let { incidencia ->
        var adrecaTransformada by remember(incidencia) { mutableStateOf(strings.issueLoadingAddressLabel) }

        LaunchedEffect(incidencia) {
            adrecaTransformada = resolveIssueAddressOrFallback(
                coordinates = incidencia.coordinates,
                fallback = strings.issueLocationFallbackLabel
            )
        }

        ReportIssueDialog(
            visible = true,
            isLoggedIn = currentUser != null,
            isEditMode = true,
            defaultLocationText = adrecaTransformada,
            initialType = issueTypeFromApi(incidencia.type),
            initialDescription = incidencia.description ?: "",
            initialCoord = incidencia.coordinates,
            onDismiss = { viewModel.cancelarEdicio() },
            onConfirm = { tipus, _, descripcio, _ ->
                currentUser?.googleId?.let { googleId ->
                    val apiType = IssueApiType.valueOf(tipus.name)
                    viewModel.guardarEdicio(
                        incidenciaId = incidencia.id,
                        nouTipus = apiType,
                        novaDescripcio = descripcio,
                        coordenades = incidencia.coordinates,
                        googleId = googleId
                    )
                }
            },
            onNavigateToLogin = onLoginClick
        )
    }

    uiState.puntInteresSeleccionat
        ?.takeIf(PuntInteres::esEsdeveniment)
        ?.let { event ->
            EventInfoDialog(
                event = event,
                canStartRoute = uiState.origenSeleccionado != null || uiState.ultimaUbicacion != null,
                onDismiss = { viewModel.onPuntInteresSeleccionat(null) },
                onRouteToEvent = { viewModel.calcularIIniciarRutaCapAPunt(event) }
            )
        }
}

@Composable
private fun BoxScope.ShareRouteOverlay(
    currentUser: UserInfo?,
    showShareRoute: Boolean,
    uiState: MapUiState,
    onBack: () -> Unit,
    onShared: () -> Unit
) {
    if (currentUser != null) {
        AnimatedVisibility(
            visible = showShareRoute,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            ShareRouteScreen(
                user = currentUser,
                originLat = uiState.origenSeleccionado?.latitude ?: 0.0,
                originLng = uiState.origenSeleccionado?.longitude ?: 0.0,
                destLat = uiState.destinoSeleccionado?.latitude ?: 0.0,
                destLng = uiState.destinoSeleccionado?.longitude ?: 0.0,
                originAddress = uiState.textoOrigen.takeIf { it.isNotBlank() },
                destAddress = uiState.textoDestino.takeIf { it.isNotBlank() },
                distanceText = uiState.distanceText,
                durationText = uiState.durationText,
                routePriority = uiState.prioridadSeleccionada,
                onBack = onBack,
                onShared = onShared
            )
        }
    }
}

@Composable
private fun BoxScope.MapActionButtons(
    uiState: MapUiState,
    isPlanningRoute: Boolean,
    isActiveRoute: Boolean,
    floatingActionsLayout: FloatingActionsLayout,
    effectiveFloatingActionsBottomPadding: Dp,
    showEmergencyAction: Boolean,
    isEmergencyActive: Boolean,
    strings: MapScreenStrings,
    viewModel: MapViewModel,
    actions: MapScreenActions,
    onEmergencyClick: () -> Unit
) {
    if (!isPlanningRoute && !isActiveRoute) {
        MapFloatingActions(
            uiState = uiState,
            state = FloatingActionsState(
                bottomPadding = effectiveFloatingActionsBottomPadding,
                compactMode = floatingActionsLayout.compactMode,
                showPoiAction = floatingActionsLayout.showPoiAction,
                showMapStyleAction = floatingActionsLayout.showMapStyleAction,
                showMyLocationAction = floatingActionsLayout.showMyLocationAction,
                showReportIssueAction = floatingActionsLayout.showReportIssueAction,
                showEmergencyAction = floatingActionsLayout.showEmergencyAction && showEmergencyAction
            ),
            labels = FloatingActionLabels(
                hideExtraInfoLabel = strings.hideExtraInfoLabel,
                showExtraInfoLabel = strings.showExtraInfoLabel,
                myLocationLabel = strings.myLocationLabel
            ),
            callbacks = FloatingActionCallbacks(
                onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
                onMyLocationClick = actions.onCenterCurrentLocation
            ),
            reportIssueLabel = strings.reportIssueLabel,
            emergencyActionLabel = strings.emergencyActionLabel,
            isEmergencyActive = isEmergencyActive,
            onReportIssueClick = { viewModel.toggleMenuIncidencies(true) },
            onEmergencyClick = onEmergencyClick
        )
    }

    if (isPlanningRoute || isActiveRoute) {
        RouteModeFloatingActions(
            uiState = uiState,
            bottomPadding = effectiveFloatingActionsBottomPadding,
            onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
            onMyLocationClick = actions.onCenterCurrentLocation,
            onReportIssueClick = { viewModel.toggleMenuIncidencies(true) },
            hideExtraInfoLabel = strings.hideExtraInfoLabel,
            showExtraInfoLabel = strings.showExtraInfoLabel,
            myLocationLabel = strings.myLocationLabel,
            reportIssueLabel = strings.reportIssueLabel,
            emergencyActionLabel = strings.emergencyActionLabel,
            isEmergencyActive = isEmergencyActive,
            showEmergencyAction = showEmergencyAction,
            onEmergencyClick = onEmergencyClick
        )
    }
}

@Composable
private fun BoxScope.MainMapSearchOverlay(
    uiState: MapUiState,
    viewModel: MapViewModel,
    currentUser: UserInfo?,
    actions: MapScreenActions,
    onLoginClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onHeightChanged: (Float) -> Unit
) {
    MainMapOverlay(
        uiState = uiState,
        accountActions = TopPanelAccountActions(
            currentUser = currentUser,
            onLoginClick = onLoginClick,
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick
        ),
        callbacks = TopSearchPanelCallbacks(
            onOrigenChange = { text ->
                viewModel.onTextoBuscadorModificado(text, textField.ORIGIN)
                if (text.isEmpty()) {
                    viewModel.limpiarOrigen()
                    viewModel.cancelarRutaVisual()
                }
            },
            onDestinoChange = { text ->
                viewModel.onTextoBuscadorModificado(text, textField.DESTINY)
                if (text.isEmpty()) {
                    viewModel.limpiarDestino()
                    viewModel.cancelarRutaVisual()
                }
            },
            onOrigenFocus = {
                viewModel.onTextoBuscadorModificado(uiState.textoOrigen, textField.ORIGIN)
            },
            onDestinoFocus = {
                viewModel.onTextoBuscadorModificado(uiState.textoDestino, textField.DESTINY)
            },
            onAdrecaSeleccionada = actions.onAddressSelected,
            onHeightChanged = onHeightChanged
        )
    )
}

@Composable
private fun BoxScope.ActiveRouteExperienceOverlay(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    currentUser: UserInfo?,
    navigationHeadingDegrees: Float?,
    actions: MapScreenActions,
    onShareRoute: () -> Unit,
    onBottomPaddingChange: (Dp) -> Unit,
    onTopBannerHeightChange: (Float) -> Unit
) {
    RouteExperienceOverlay(
        uiState = uiState,
        showProfilePreferences = !currentUser?.googleId.isNullOrBlank(),
        onPrioritySelected = actions.onPrioritySelected,
        onStartRoute = {
            when (viewModel.iniciarRuta()) {
                ActiveRouteMode.USER_LOCATION_NAVIGATION -> {
                    uiState.ultimaUbicacion?.let { location ->
                        enableNavigationCameraTracking(
                            mapView = mapView,
                            currentLocation = location,
                            headingDegrees = navigationHeadingDegrees?.toDouble(),
                            applyZoom = true
                        )
                    }
                }

                ActiveRouteMode.FIXED_OVERVIEW,
                ActiveRouteMode.NONE -> {
                    disableNavigationCameraTracking(mapView)
                }
            }
        },
        onClose = actions.resetToMainMenu,
        onShareRoute = onShareRoute,
        onBottomPaddingChange = onBottomPaddingChange,
        onTopBannerHeightChange = onTopBannerHeightChange
    )
}

private fun handleEmergencyHelpRouteClick(
    uiState: MapUiState,
    strings: MapScreenStrings,
    viewModel: MapViewModel
) {
    val pendingLocation = uiState.pendingEmergencyContactLocation ?: return
    val hasRouteOrigin = uiState.origenSeleccionado != null || uiState.ultimaUbicacion != null
    if (!hasRouteOrigin) {
        ScreenNotificationManager.showNotification(
            notificationName = strings.mapNotificationTitle,
            text = strings.waitingGpsLocationMessage
        )
        return
    }
    viewModel.prepareEmergencyHelpRoute(pendingLocation)
}

@Composable
private fun BoxScope.EmergencyContactHelpOverlayWrapper(
    uiState: MapUiState,
    strings: MapScreenStrings,
    emergencyHelpBottomPadding: Dp,
    mapView: MapView,
    viewModel: MapViewModel
) {
    EmergencyContactHelpOverlay(
        pendingLocation = uiState.pendingEmergencyContactLocation,
        titleFallback = strings.emergencyContactHelpTitle,
        actionLabel = strings.emergencyContactHelpRouteAction,
        dismissLabel = strings.emergencyContactDismissLabel,
        bottomPadding = emergencyHelpBottomPadding,
        onFocusLocationClick = {
            focusOnEmergencyContactLocation(
                mapView = mapView,
                uiState = uiState,
                viewModel = viewModel
            )
        },
        onHelpRouteClick = { handleEmergencyHelpRouteClick(uiState, strings, viewModel) },
        onDismissClick = {
            uiState.pendingEmergencyContactLocation?.id?.let(viewModel::dismissEmergencyContactLocation)
        }
    )
}

@Composable
private fun BoxScope.MapScreenInteractiveActions(
    uiState: MapUiState,
    isPlanningRoute: Boolean,
    isActiveRoute: Boolean,
    floatingActionsLayout: FloatingActionsLayout,
    effectiveFloatingActionsBottomPadding: Dp,
    emergencyHelpBottomPadding: Dp,
    mapStyleTopOffset: Dp,
    showEmergencyAction: Boolean,
    isEmergencyActive: Boolean,
    strings: MapScreenStrings,
    viewModel: MapViewModel,
    actions: MapScreenActions,
    mapView: MapView,
    onEmergencyClick: () -> Unit
) {
    MapActionButtons(
        uiState = uiState,
        isPlanningRoute = isPlanningRoute,
        isActiveRoute = isActiveRoute,
        floatingActionsLayout = floatingActionsLayout,
        effectiveFloatingActionsBottomPadding = effectiveFloatingActionsBottomPadding,
        showEmergencyAction = showEmergencyAction,
        isEmergencyActive = isEmergencyActive,
        strings = strings,
        viewModel = viewModel,
        actions = actions,
        onEmergencyClick = onEmergencyClick
    )

    MapStyleTopRightOverlay(
        visible = floatingActionsLayout.showMapStyleAction,
        isSatelliteStyle = uiState.estiloSatelite,
        topOffset = mapStyleTopOffset,
        standardMapStyleLabel = strings.standardMapStyleLabel,
        satelliteMapStyleLabel = strings.satelliteMapStyleLabel,
        onClick = { viewModel.toggleEstiloSatelite() }
    )

    if (isPlanningRoute || isActiveRoute) {
        RouteModeFloatingActions(
            uiState = uiState,
            bottomPadding = effectiveFloatingActionsBottomPadding,
            onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
            onMyLocationClick = actions.onCenterCurrentLocation,
            onReportIssueClick = { viewModel.toggleMenuIncidencies(true) },
            hideExtraInfoLabel = strings.hideExtraInfoLabel,
            showExtraInfoLabel = strings.showExtraInfoLabel,
            myLocationLabel = strings.myLocationLabel,
            reportIssueLabel = strings.reportIssueLabel,
            emergencyActionLabel = strings.emergencyActionLabel,
            isEmergencyActive = isEmergencyActive,
            showEmergencyAction = showEmergencyAction,
            onEmergencyClick = onEmergencyClick
        )
    }

    if (!isPlanningRoute && !isActiveRoute) {
        EmergencyContactHelpOverlayWrapper(
            uiState = uiState,
            strings = strings,
            emergencyHelpBottomPadding = emergencyHelpBottomPadding,
            mapView = mapView,
            viewModel = viewModel
        )
    }
}

@Composable
private fun MapScreenContent(
    modifier: Modifier,
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    currentUser: UserInfo?,
    showEmergencyAction: Boolean,
    isEmergencyActive: Boolean,
    strings: MapScreenStrings,
    actions: MapScreenActions,
    onMapReady: (MapLibreMap) -> Unit,
    navigationHeadingDegrees: Float?,
    onEmergencyClick: () -> Unit,
    onLoginClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onVoted: () -> Unit = {}
) {
    var floatingActionsBottomPadding by remember { mutableStateOf(16.dp) }
    var topOverlayHeightPx by remember { mutableFloatStateOf(0f) }
    var routeTopBannerHeightPx by remember { mutableFloatStateOf(0f) }
    var showShareRoute by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(uiState.modoRuta) {
        if (uiState.modoRuta) {
            topOverlayHeightPx = 0f
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val navigationBarsBottomPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val statusBarsTopPadding = WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()
        val effectiveFloatingActionsBottomPadding = maxOf(
            floatingActionsBottomPadding,
            navigationBarsBottomPadding + 16.dp
        )
        val availableHeightPx = with(density) {
            maxHeight.toPx() - effectiveFloatingActionsBottomPadding.toPx() - topOverlayHeightPx
        }
        val mapStyleTopOffset = when {
            !uiState.modoRuta && topOverlayHeightPx > 0f -> with(density) { topOverlayHeightPx.toDp() } + 12.dp
            uiState.usesLiveNavigation && !uiState.routeCompleted && routeTopBannerHeightPx > 0f -> {
                with(density) { routeTopBannerHeightPx.toDp() } + 12.dp
            }
            else -> statusBarsTopPadding + 16.dp
        }
        val floatingActionsLayout = resolveFloatingActionsLayout(
            freeHeightPx = availableHeightPx,
            density = density,
            hasPoiAction = uiState.puntsInteres.isNotEmpty(),
            hasEmergencyAction = showEmergencyAction
        )
        val emergencyHelpBottomPadding = navigationBarsBottomPadding + 18.dp

        val isPlanningRoute = uiState.destinoSeleccionado != null && !uiState.modoRuta
        val isActiveRoute = uiState.modoRuta

        MapViewSurface(
            mapView = mapView,
            uiState = uiState,
            viewModel = viewModel,
            restrictedLocationMessage = strings.mapLocationSelectionRestrictedMessage,
            onMapReady = onMapReady
        )

        MainMapSearchOverlay(
            uiState = uiState,
            viewModel = viewModel,
            currentUser = currentUser,
            actions = actions,
            onLoginClick = onLoginClick,
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick,
            onHeightChanged = { topOverlayHeightPx = it }
        )

        ActiveRouteExperienceOverlay(
            uiState = uiState,
            viewModel = viewModel,
            mapView = mapView,
            currentUser = currentUser,
            navigationHeadingDegrees = navigationHeadingDegrees,
            actions = actions,
            onShareRoute = { showShareRoute = true },
            onBottomPaddingChange = { padding ->
                floatingActionsBottomPadding = padding
            },
            onTopBannerHeightChange = { heightPx ->
                routeTopBannerHeightPx = heightPx
            }
        )

        ShareRouteOverlay(
            currentUser = currentUser,
            showShareRoute = showShareRoute,
            uiState = uiState,
            onBack = { showShareRoute = false },
            onShared = { showShareRoute = false }
        )

        if (!showShareRoute) {
            MapScreenInteractiveActions(
                uiState = uiState,
                isPlanningRoute = isPlanningRoute,
                isActiveRoute = isActiveRoute,
                floatingActionsLayout = floatingActionsLayout,
                effectiveFloatingActionsBottomPadding = effectiveFloatingActionsBottomPadding,
                emergencyHelpBottomPadding = emergencyHelpBottomPadding,
                mapStyleTopOffset = mapStyleTopOffset,
                showEmergencyAction = showEmergencyAction,
                isEmergencyActive = isEmergencyActive,
                strings = strings,
                viewModel = viewModel,
                actions = actions,
                mapView = mapView,
                onEmergencyClick = onEmergencyClick
            )
        }

        CalculatingRouteOverlay(
            visible = uiState.calculantRuta,
            calculatingBestRouteLabel = strings.calculatingBestRouteLabel
        )

        MapScreenDialogs(
            uiState = uiState,
            viewModel = viewModel,
            currentUser = currentUser,
            strings = strings,
            context = LocalContext.current,
            onLoginClick = onLoginClick,
            onVoted = onVoted
        )
    }
}

@Stable
private class LevelUpManager(
    showOverlay: Boolean = false,
    level: Long = 1L
) {
    var showOverlay by mutableStateOf(showOverlay)
    var level by mutableStateOf(level)
    var pendingCheck by mutableStateOf(false)

    fun triggerCheck() {
        pendingCheck = true
    }

    fun dismissOverlay() {
        showOverlay = false
    }
}

@Composable
private fun rememberLevelUpManager(
    currentUser: UserInfo?,
    routeResult: RouteCompletionResponse?,
    onRouteCompleted: (RouteCompletionResponse) -> Unit,
    onRouteResultDismissed: () -> Unit,
    onVoted: () -> Unit
): LevelUpManager {
    val manager = remember { LevelUpManager() }

    LaunchedEffect(routeResult) {
        if (routeResult != null) {
            onRouteCompleted(routeResult)
            if (routeResult.levelUpdated == true) {
                manager.level = routeResult.level ?: 1
                manager.showOverlay = true
            }
            onRouteResultDismissed()
        }
    }
    LaunchedEffect(manager.pendingCheck) {
        if (!manager.pendingCheck) return@LaunchedEffect
        manager.pendingCheck = false
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val profileBefore = com.safesteps.data.cargarPerfilDeUsuario(googleId) ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        val profileAfter = com.safesteps.data.cargarPerfilDeUsuario(googleId) ?: return@LaunchedEffect
        if ((profileAfter.level ?: 1) > (profileBefore.level ?: 1)) {
            manager.level = profileAfter.level ?: 1
            manager.showOverlay = true
        }
        onVoted()
    }
    return manager
}

@Stable
private class EmergencyManager(
    hasContacts: Boolean = false,
    isActive: Boolean = false
) {
    var hasContacts by mutableStateOf(hasContacts)
    var isActive by mutableStateOf(isActive)
}

@Composable
private fun rememberEmergencyManager(
    currentUser: UserInfo?,
    latestLocation: Location?
): EmergencyManager {
    val manager = remember { EmergencyManager() }

    LaunchedEffect(currentUser?.googleId) {
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
        if (googleId == null) {
            manager.hasContacts = false
            manager.isActive = false
            return@LaunchedEffect
        }

        manager.hasContacts = runCatching {
            cargarContactosEmergenciaUsuario(googleId).isNotEmpty()
        }.getOrDefault(false)

        manager.isActive = runCatching {
            obtenerEstadoEmergenciaUsuario(googleId)
        }.getOrDefault(false)
    }

    val latestEmergencyLocation = rememberUpdatedState(latestLocation)
    LaunchedEffect(manager.isActive) {
        if (!manager.isActive) return@LaunchedEffect
        while (true) {
            latestEmergencyLocation.value?.let { location ->
                BackendWebSocketManager.sendLocationUpdate(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
            kotlinx.coroutines.delay(EmergencyLocationBroadcastIntervalMillis)
        }
    }
    return manager
}

@Composable
private fun rememberDisplayedUiState(
    uiState: MapUiState,
    context: Context,
    onClearSelectedPoi: () -> Unit
): MapUiState {
    val eventSettings by remember(context.applicationContext) {
        MapEventPreferences.settings(context.applicationContext)
    }.collectAsState()
    
    val shouldHideRouteEvents = remember(uiState, eventSettings) {
        resolveShouldHideRouteEvents(
            uiState = uiState,
            settings = eventSettings
        )
    }
    
    val displayedPuntsInteres = remember(uiState.puntsInteres, shouldHideRouteEvents) {
        filterDisplayedPuntsInteres(
            puntsInteres = uiState.puntsInteres,
            hideRouteEvents = shouldHideRouteEvents
        )
    }
    
    val displayedSelectedPoi = remember(uiState.puntInteresSeleccionat, displayedPuntsInteres) {
        uiState.puntInteresSeleccionat?.takeIf { selectedPoi ->
            displayedPuntsInteres.any { it.id == selectedPoi.id }
        }
    }
    
    LaunchedEffect(shouldHideRouteEvents, uiState.puntInteresSeleccionat?.id) {
        if (shouldHideRouteEvents && uiState.puntInteresSeleccionat?.esEsdeveniment() == true) {
            onClearSelectedPoi()
        }
    }
    
    return remember(uiState, displayedPuntsInteres, displayedSelectedPoi) {
        uiState.copy(
            puntsInteres = displayedPuntsInteres,
            puntInteresSeleccionat = displayedSelectedPoi
        )
    }
}

@Composable
fun MapLibreScreen(
    modifier: Modifier = Modifier,
    currentUser: UserInfo? = null,
    currentLanguage: AppLanguage = AppLanguage.default,
    issuesRefreshTrigger: Int = 0,
    onLoginClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onRouteCompleted: (RouteCompletionResponse) -> Unit = {},
    onVoted: () -> Unit = {},
    pendingRoute: PendingRoute? = null,
){
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.applicationContext)
    )
    val mapView = rememberMapViewWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()
    
    val displayedUiState = rememberDisplayedUiState(
        uiState = uiState,
        context = context,
        onClearSelectedPoi = { viewModel.onPuntInteresSeleccionat(null) }
    )

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            viewModel.calcularRuta(
                origenLong = pendingRoute.originLng,
                origenLat = pendingRoute.originLat,
                destiLong = pendingRoute.destLng,
                destiLat = pendingRoute.destLat
            )
        }
    }

    val levelUpManager = rememberLevelUpManager(
        currentUser = currentUser,
        routeResult = viewModel.routeResult,
        onRouteCompleted = onRouteCompleted,
        onRouteResultDismissed = { viewModel.dismissRouteResult() },
        onVoted = onVoted
    )

    val emergencyManager = rememberEmergencyManager(
        currentUser = currentUser,
        latestLocation = uiState.ultimaUbicacion
    )

    var attachedMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var routeExitCameraSnapshot by remember { mutableStateOf<RouteExitCameraSnapshot?>(null) }
    var navigationHeadingDegrees by remember { mutableStateOf<Float?>(null) }

    val strings = mapScreenStrings()
    val onEmergencyClick = rememberEmergencyStatusToggleAction(
        currentUser = currentUser,
        strings = strings,
        onEmergencyStateChanged = { emergencyManager.isActive = it }
    )

    val actions = rememberMapScreenActions(
        uiState = uiState,
        viewModel = viewModel,
        mapView = mapView,
        map = attachedMap,
        context = context,
        strings = strings,
        navigationHeadingDegrees = navigationHeadingDegrees,
        routeExitCameraSnapshot = routeExitCameraSnapshot,
        onRouteExitCameraSnapshotCaptured = { snapshot ->
            routeExitCameraSnapshot = snapshot
        },
        onRouteExitCameraSnapshotConsumed = {
            routeExitCameraSnapshot = null
        }
    )

    val renderContext = MapRenderContext(
        mapView = mapView,
        context = context,
        viewModel = viewModel,
        navigationHeadingDegrees = navigationHeadingDegrees,
        originLabel = strings.originLabel,
        destinationLabel = strings.destinationLabel,
        emergencyContactMarkerLabel = strings.emergencyContactMarkerLabel
    )

    LaunchedEffect(currentUser?.googleId) {
        viewModel.onCurrentUserChanged(currentUser)
    }

    LaunchedEffect(issuesRefreshTrigger) {
        viewModel.loadIssuesMap()
    }

    LaunchedEffect(viewModel) {
        BackendWebSocketManager.locationEvents.collectLatest { event ->
            viewModel.onEmergencyContactLocationReceived(event)
        }
    }

    MapScreenEffects(
        currentLanguage = currentLanguage,
        uiState = displayedUiState,
        renderContext = renderContext,
        mapNotificationTitle = strings.mapNotificationTitle,
        callbacks = MapScreenEffectCallbacks(
            requestLocationPermissions = actions.requestLocationPermissions,
            onNavigationHeadingChanged = { navigationHeadingDegrees = it }
        )
    )

    MapScreenContent(
        modifier = modifier,
        uiState = displayedUiState,
        viewModel = viewModel,
        mapView = mapView,
        currentUser = currentUser,
        showEmergencyAction = emergencyManager.hasContacts,
        isEmergencyActive = emergencyManager.isActive,
        strings = strings,
        actions = actions,
        onMapReady = { map -> attachedMap = map },
        navigationHeadingDegrees = navigationHeadingDegrees,
        onEmergencyClick = onEmergencyClick,
        onLoginClick = onLoginClick,
        onMenuClick = onMenuClick,
        onProfileClick = onProfileClick,
        onVoted = { levelUpManager.triggerCheck() }
    )

    if (levelUpManager.showOverlay) {
        com.safesteps.profile.LevelUpAnimationOverlay(
            level = levelUpManager.level,
            onDismiss = { levelUpManager.dismissOverlay() }
        )
    }
}

@Composable
private fun BoxScope.RouteModeFloatingActions(
    uiState: MapUiState,
    bottomPadding: Dp,
    onTogglePuntsInteres: () -> Unit,
    onMyLocationClick: () -> Unit,
    onReportIssueClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    hideExtraInfoLabel: String,
    showExtraInfoLabel: String,
    myLocationLabel: String,
    reportIssueLabel: String,
    emergencyActionLabel: String,
    isEmergencyActive: Boolean,
    showEmergencyAction: Boolean
) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.puntsInteres.isNotEmpty()) {
            CompactCircularMapAction(
                onClick = onTogglePuntsInteres,
                contentDescription = if (uiState.mostrarPuntsInteres) hideExtraInfoLabel else showExtraInfoLabel,
                icon = Icons.Default.Place,
                tint = if (uiState.mostrarPuntsInteres) Color(0xFF388E6C) else Color(0xFF6D7B75),
                testTagId = "btn_places"
            )
        }
        CompactCircularMapAction(
            onClick = onMyLocationClick,
            contentDescription = myLocationLabel,
            icon = Icons.Default.MyLocation,
            tint = Color(0xFF2A5F8A),
            testTagId = "btn_ubicacio_actual"
        )
        CompactCircularMapAction(
            onClick = onReportIssueClick,
            contentDescription = reportIssueLabel,
            icon = Icons.Default.ReportProblem,
            tint = Color.White,
            backgroundColor = Color(0xFFC86A37),
            testTagId = "btn_incidencies"
        )
        if (showEmergencyAction) {
            CompactCircularMapAction(
                onClick = onEmergencyClick,
                contentDescription = emergencyActionLabel,
                icon = Icons.Default.NotificationsActive,
                tint = Color.White,
                backgroundColor = Color(0xFFB71C3B),
                borderColor = Color(0xFFFFC8D4),
                modifier = rememberEmergencyHeartbeatModifier(isEmergencyActive),
                testTagId = "btn_emergency"
            )
        }
    }
}

@Composable
private fun CompactCircularMapAction(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    tint: Color = Color(0xFF33413B),
    backgroundColor: Color = Color.White,
    borderColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    testTagId: String? = null
) {
    Surface(
        shape = CircleShape,
        modifier = modifier
            .size(44.dp)
            .border(1.dp, borderColor, CircleShape),
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = if (testTagId != null) {
                Modifier
                    .semantics(mergeDescendants = true) { testTag = testTagId }
                    .testTag(testTagId)
            } else {
                Modifier
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.EmergencyContactHelpOverlay(
    pendingLocation: EmergencyContactLocation?,
    titleFallback: String,
    actionLabel: String,
    dismissLabel: String,
    bottomPadding: Dp,
    onFocusLocationClick: () -> Unit,
    onHelpRouteClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    if (pendingLocation == null) {
        return
    }

    val resolvedTitle = normalizeEmergencyBannerText(pendingLocation.username)
        ?.let { username ->
            appString(R.string.emergency_contact_help_title_with_name, username)
        }
        ?: normalizeEmergencyBannerText(pendingLocation.title)
        ?: titleFallback
    val formattedLastSeenTime = DateFormat.getTimeInstance(
        DateFormat.SHORT,
        Locale.getDefault()
    ).format(Date(pendingLocation.receivedAtMillis))
    val resolvedLastSeenText = appString(
        R.string.emergency_contact_last_seen_format,
        formattedLastSeenTime
    )

    Surface(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, end = 104.dp, bottom = bottomPadding)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFCFC),
                            Color(0xFFFFF1F3)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(1.dp, Color(0xFFFFD6DE), RoundedCornerShape(28.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onFocusLocationClick)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFB71C3B)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFFBE0E5)
                        ) {
                            Text(
                                text = "SOS",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color(0xFFB71C3B),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6E1730),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = resolvedLastSeenText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8A4A5B),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFFFFF4F5), CircleShape)
                        .border(1.dp, Color(0xFFFFD6DE), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = dismissLabel,
                        tint = Color(0xFF9C4A4A),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onHelpRouteClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C3B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun normalizeEmergencyBannerText(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

@Composable
private fun mapScreenStrings(): MapScreenStrings {
    return MapScreenStrings(
        mapNotificationTitle = appString(R.string.notification_title_map),
        permissionNotificationTitle = appString(R.string.notification_title_permissions),
        locationPermissionRequiredMessage = appString(R.string.location_permission_required),
        waitingGpsLocationMessage = appString(R.string.waiting_gps_location),
        searchingGpsSignalMessage = appString(R.string.searching_gps_signal),
        originLabel = appString(R.string.origin_label),
        destinationLabel = appString(R.string.destination_label),
        hideExtraInfoLabel = appString(R.string.hide_extra_info),
        showExtraInfoLabel = appString(R.string.show_extra_info),
        standardMapStyleLabel = appString(R.string.map_style_standard),
        satelliteMapStyleLabel = appString(R.string.map_style_satellite),
        myLocationLabel = appString(R.string.my_location),
        calculatingBestRouteLabel = appString(R.string.calculating_best_route),
        reportIssueLabel = appString(R.string.report_issue),
        emergencyActionLabel = appString(R.string.map_emergency_action),
        emergencyModeActivatedMessage = appString(R.string.emergency_mode_activated),
        emergencyModeDeactivatedMessage = appString(R.string.emergency_mode_deactivated),
        emergencyModeUpdateFailedMessage = appString(R.string.emergency_mode_update_failed),
        emergencyContactHelpTitle = appString(R.string.emergency_contact_help_title),
        emergencyContactHelpRouteAction = appString(R.string.emergency_contact_help_route_action),
        emergencyContactMarkerLabel = appString(R.string.emergency_contact_marker_label),
        emergencyContactDismissLabel = appString(R.string.emergency_contact_dismiss_label),
        reportIssueOutsideBarcelonaMessage = appString(R.string.issue_report_outside_barcelona),
        issueLoadingAddressLabel = appString(R.string.issue_loading_address),
        issueLocationFallbackLabel = appString(R.string.issue_location_fallback),
        mapLocationSelectionRestrictedMessage = appString(R.string.map_location_selection_restricted)
    )
}

@Composable
private fun rememberEmergencyStatusToggleAction(
    currentUser: UserInfo?,
    strings: MapScreenStrings,
    onEmergencyStateChanged: (Boolean) -> Unit
): () -> Unit {
    val coroutineScope = rememberCoroutineScope()
    val latestUser = rememberUpdatedState(currentUser)
    val latestStrings = rememberUpdatedState(strings)
    val latestOnEmergencyStateChanged = rememberUpdatedState(onEmergencyStateChanged)

    return remember(coroutineScope) {
        {
            coroutineScope.launch {
                val resolvedUser = latestUser.value ?: return@launch
                val googleId = resolvedUser.googleId.takeIf { it.isNotBlank() } ?: return@launch

                runCatching {
                    alternarEstadoEmergenciaUsuario(googleId)
                }.onSuccess { isActive ->
                    latestOnEmergencyStateChanged.value(isActive)
                    ScreenNotificationManager.showNotification(
                        notificationName = latestStrings.value.emergencyActionLabel,
                        durationMillis = EmergencyStatusNotificationDurationMillis,
                        text = if (isActive) {
                            latestStrings.value.emergencyModeActivatedMessage
                        } else {
                            latestStrings.value.emergencyModeDeactivatedMessage
                        }
                    )
                }.onFailure {
                    ScreenNotificationManager.showNotification(
                        notificationName = latestStrings.value.emergencyActionLabel,
                        text = latestStrings.value.emergencyModeUpdateFailedMessage
                    )
                }
            }
        }
    }
}

private fun focusOnEmergencyContactLocation(
    mapView: MapView,
    uiState: MapUiState,
    viewModel: MapViewModel
) {
    val pendingLocation = uiState.pendingEmergencyContactLocation ?: return

    if (
        uiState.modoRuta &&
        !uiState.routeCompleted &&
        uiState.navigationCameraFollowing
    ) {
        viewModel.onNavigationCameraDismissedByGesture()
    }

    mapView.getMapAsync { map ->
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(pendingLocation.latitude, pendingLocation.longitude),
                16.0
            ),
            1200
        )
    }
}

@Composable
private fun rememberLocationPermissionRequester(
    context: Context,
    viewModel: MapViewModel,
    permissionNotificationTitle: String,
    locationPermissionRequiredMessage: String
): () -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        processLocationPermissionResult(
            context = context,
            permissions = permissions,
            viewModel = viewModel,
            permissionNotificationTitle = permissionNotificationTitle,
            locationPermissionRequiredMessage = locationPermissionRequiredMessage
        )
    }

    return {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@Composable
private fun MapScreenEffects(
    currentLanguage: AppLanguage,
    uiState: MapUiState,
    renderContext: MapRenderContext,
    mapNotificationTitle: String,
    callbacks: MapScreenEffectCallbacks
) {
    PrepareMapSessionEffect(
        mapView = renderContext.mapView,
        viewModel = renderContext.viewModel
    )
    LanguageChangeEffect(
        currentLanguage = currentLanguage,
        viewModel = renderContext.viewModel
    )
    InitialLocationPermissionEffect(
        context = renderContext.context,
        viewModel = renderContext.viewModel,
        requestLocationPermissions = callbacks.requestLocationPermissions
    )
    LocationComponentActivationEffect(
        locationGranted = uiState.locationGranted,
        mapaListo = uiState.mapaListo,
        ultimaUbicacion = uiState.ultimaUbicacion,
        mapView = renderContext.mapView
    )
    NavigationHeadingSensorEffect(
        context = renderContext.context,
        enabled = uiState.usesLiveNavigation && !uiState.routeCompleted && uiState.navigationCameraFollowing,
        onHeadingChanged = callbacks.onNavigationHeadingChanged
    )
    NavigationCameraTrackingEffect(
        trackingState = NavigationTrackingState(
            locationGranted = uiState.locationGranted,
            mapaListo = uiState.mapaListo,
            modoRuta = uiState.usesLiveNavigation,
            routeCompleted = uiState.routeCompleted,
            navigationCameraFollowing = uiState.navigationCameraFollowing,
            currentLocation = uiState.ultimaUbicacion
        ),
        mapView = renderContext.mapView,
        headingDegrees = renderContext.navigationHeadingDegrees
    )
    NavigationCameraGestureDismissEffect(
        mapView = renderContext.mapView,
        uiState = uiState,
        viewModel = renderContext.viewModel
    )
    RouteRecalculationEffect(
        destinoSeleccionado = uiState.destinoSeleccionado,
        origenSeleccionado = uiState.origenSeleccionado,
        ultimaUbicacion = uiState.ultimaUbicacion,
        viewModel = renderContext.viewModel
    )
    NavigationNoticeEffect(
        navigationNotice = uiState.navigationNotice,
        notificationTitle = mapNotificationTitle,
        viewModel = renderContext.viewModel
    )
    MapStyleRenderingEffect(
        uiState = uiState,
        renderContext = renderContext
    )
    LocationUpdatesEffect(
        locationGranted = uiState.locationGranted,
        context = renderContext.context,
        mapView = renderContext.mapView,
        viewModel = renderContext.viewModel
    )
    InitialZoomEffect(
        ultimaUbicacion = uiState.ultimaUbicacion,
        mapaListo = uiState.mapaListo,
        firstLocationZoomDone = uiState.firstLocationZoomDone,
        mapView = renderContext.mapView,
        viewModel = renderContext.viewModel
    )
}

@Composable
private fun PrepareMapSessionEffect(
    mapView: MapView,
    viewModel: MapViewModel
) {
    LaunchedEffect(mapView) {
        viewModel.prepararNuevaSesionMapa()
    }
}

@Composable
private fun LanguageChangeEffect(
    currentLanguage: AppLanguage,
    viewModel: MapViewModel
) {
    LaunchedEffect(currentLanguage) {
        viewModel.onLanguageChanged(currentLanguage)
    }
}

@Composable
private fun InitialLocationPermissionEffect(
    context: Context,
    viewModel: MapViewModel,
    requestLocationPermissions: () -> Unit
) {
    LaunchedEffect(Unit) {
        val hasPermission = hasLocationPermission(context)
        viewModel.onLocationPermissionsResult(hasPermission)

        if (hasPermission) {
            getBestLastKnownLocation(context)?.let(viewModel::updateLocation)
            return@LaunchedEffect
        }

        requestLocationPermissions()
    }
}

@Composable
private fun LocationComponentActivationEffect(
    locationGranted: Boolean,
    mapaListo: Boolean,
    ultimaUbicacion: Location?,
    mapView: MapView
) {
    LaunchedEffect(locationGranted, mapaListo, ultimaUbicacion != null) {
        if (locationGranted && mapaListo) {
            activateLocationComponent(
                mapView = mapView,
                initialLocation = ultimaUbicacion
            )
        }
    }
}

@Composable
private fun NavigationHeadingSensorEffect(
    context: Context,
    enabled: Boolean,
    onHeadingChanged: (Float?) -> Unit
) {
    DisposableEffect(context, enabled) {
        if (!enabled) {
            onHeadingChanged(null)
            return@DisposableEffect onDispose { }
        }

        val listener = startHeadingUpdates(context, onHeadingChanged)
        onDispose {
            stopHeadingUpdates(context, listener)
            onHeadingChanged(null)
        }
    }
}

@Composable
private fun RouteRecalculationEffect(
    destinoSeleccionado: LatLng?,
    origenSeleccionado: LatLng?,
    ultimaUbicacion: Location?,
    viewModel: MapViewModel
) {
    LaunchedEffect(destinoSeleccionado, origenSeleccionado) {
        requestRouteCalculation(
            viewModel = viewModel,
            destination = destinoSeleccionado,
            selectedOrigin = origenSeleccionado,
            currentLocation = ultimaUbicacion
        )
    }
}

@Composable
private fun NavigationNoticeEffect(
    navigationNotice: String?,
    notificationTitle: String,
    viewModel: MapViewModel
) {
    LaunchedEffect(navigationNotice) {
        if (navigationNotice.isNullOrBlank()) {
            return@LaunchedEffect
        }

        ScreenNotificationManager.showNotification(
            notificationName = notificationTitle,
            text = navigationNotice
        )
        viewModel.onNavigationNoticeConsumed()
    }
}

@Composable
private fun MapStyleRenderingEffect(
    uiState: MapUiState,
    renderContext: MapRenderContext
) {
    LaunchedEffect(
        uiState.estiloSatelite,
        uiState.modoRuta,
        uiState.rutaCoordenades,
        uiState.mostrarPuntsInteres,
        uiState.puntsInteres,
        uiState.origenSeleccionado,
        uiState.destinoSeleccionado,
        uiState.issues,
        uiState.emergencyContactLocations,
        uiState.pendingEmergencyContactLocation?.id
    ) {
        val targetStyleUrl = resolveMapStyleUrl(uiState.estiloSatelite)
        renderContext.mapView.getMapAsync { map ->
            val currentStyle = map.style
            if (currentStyle?.isFullyLoaded == true && currentStyle.uri == targetStyleUrl) {
                renderMapStateAfterStyleLoaded(
                    map = map,
                    uiState = uiState,
                    renderContext = renderContext
                )
                return@getMapAsync
            }

            map.setStyle(targetStyleUrl) {
                renderMapStateAfterStyleLoaded(
                    map = map,
                    uiState = uiState,
                    renderContext = renderContext
                )
            }
        }
    }
}

private fun resolveFloatingActionsLayout(
    freeHeightPx: Float,
    density: androidx.compose.ui.unit.Density,
    hasPoiAction: Boolean,
    hasEmergencyAction: Boolean
): FloatingActionsLayout {
    val safeFreeHeightPx = (freeHeightPx - with(density) { 24.dp.toPx() }).coerceAtLeast(0f)
    val layoutCandidates = floatingActionsLayoutCandidates(
        hasPoiAction = hasPoiAction,
        hasEmergencyAction = hasEmergencyAction
    )

    return layoutCandidates.firstOrNull { layout ->
        safeFreeHeightPx >= with(density) { estimatedFloatingActionsHeight(layout).toPx() }
    } ?: layoutCandidates.last()
}

private fun floatingActionsLayoutCandidates(
    hasPoiAction: Boolean,
    hasEmergencyAction: Boolean
): List<FloatingActionsLayout> {
    return listOf(
        FloatingActionsLayout(
            compactMode = false,
            showPoiAction = hasPoiAction,
            showMapStyleAction = true,
            showMyLocationAction = true,
            showReportIssueAction = true,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = hasPoiAction,
            showMapStyleAction = true,
            showMyLocationAction = true,
            showReportIssueAction = true,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = true,
            showReportIssueAction = true,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = true,
            showReportIssueAction = true,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = true,
            showReportIssueAction = false,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = false,
            showReportIssueAction = false,
            showEmergencyAction = hasEmergencyAction
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = false,
            showReportIssueAction = false,
            showEmergencyAction = false
        )
    )
}

private fun estimatedFloatingActionsHeight(layout: FloatingActionsLayout): Dp {
    val itemHeights = listOfNotNull(
        floatingActionHeightOrNull(layout.showPoiAction, layout.compactMode, compactHeight = 34.dp, expandedHeight = 40.dp),
        floatingActionHeightOrNull(layout.showReportIssueAction, layout.compactMode, compactHeight = 46.dp, expandedHeight = 54.dp),
        floatingActionHeightOrNull(layout.showEmergencyAction, layout.compactMode, compactHeight = 46.dp, expandedHeight = 54.dp),
        floatingActionHeightOrNull(layout.showMyLocationAction, layout.compactMode, compactHeight = 46.dp, expandedHeight = 54.dp)
    )

    if (itemHeights.isEmpty()) {
        return 0.dp
    }

    val spacing = if (layout.compactMode) 8.dp else 10.dp
    return itemHeights.reduce { total, item -> total + item } + spacing * (itemHeights.size - 1)
}

private fun floatingActionHeightOrNull(
    visible: Boolean,
    compactMode: Boolean,
    compactHeight: Dp,
    expandedHeight: Dp
): Dp? {
    if (!visible) {
        return null
    }

    return if (compactMode) compactHeight else expandedHeight
}

@Composable
private fun LocationUpdatesEffect(
    locationGranted: Boolean,
    context: Context,
    mapView: MapView,
    viewModel: MapViewModel
) {
    DisposableEffect(locationGranted) {
        val listener = startLocationListenerIfNeeded(
            locationGranted = locationGranted,
            context = context,
            mapView = mapView,
            viewModel = viewModel
        )

        onDispose {
            stopAndroidLocationUpdates(context, listener)
        }
    }
}

@Composable
private fun InitialZoomEffect(
    ultimaUbicacion: Location?,
    mapaListo: Boolean,
    firstLocationZoomDone: Boolean,
    mapView: MapView,
    viewModel: MapViewModel
) {
    LaunchedEffect(ultimaUbicacion, mapaListo) {
        if (ultimaUbicacion != null && mapaListo && !firstLocationZoomDone) {
            centerMapOnLocation(
                mapView = mapView,
                location = ultimaUbicacion,
                durationMs = 1500
            )
            viewModel.marcarZoomInicialHecho()
        }
    }
}

@Composable
private fun NavigationCameraTrackingEffect(
    trackingState: NavigationTrackingState,
    mapView: MapView,
    headingDegrees: Float?
) {
    LaunchedEffect(
        trackingState.locationGranted,
        trackingState.mapaListo,
        trackingState.modoRuta,
        trackingState.routeCompleted,
        trackingState.navigationCameraFollowing,
        trackingState.currentLocation?.latitude,
        trackingState.currentLocation?.longitude,
        trackingState.currentLocation?.bearing,
        headingDegrees?.toInt()
    ) {
        if (!trackingState.locationGranted || !trackingState.mapaListo) {
            return@LaunchedEffect
        }

        syncNavigationCameraTracking(
            mapView = mapView,
            modoRuta = trackingState.modoRuta,
            routeCompleted = trackingState.routeCompleted,
            navigationCameraFollowing = trackingState.navigationCameraFollowing,
            currentLocation = trackingState.currentLocation,
            headingDegrees = headingDegrees?.toDouble(),
            applyZoom = false
        )
    }
}

@Composable
private fun NavigationCameraGestureDismissEffect(
    mapView: MapView,
    uiState: MapUiState,
    viewModel: MapViewModel
) {
    val latestUiState = rememberUpdatedState(uiState)

    DisposableEffect(mapView, viewModel) {
        var attachedMap: MapLibreMap? = null
        var disposed = false
        val listener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            val state = latestUiState.value
            if (
                reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE &&
                state.modoRuta &&
                !state.routeCompleted &&
                state.navigationCameraFollowing
            ) {
                viewModel.onNavigationCameraDismissedByGesture()
            }
        }

        mapView.getMapAsync { map ->
            if (disposed) {
                return@getMapAsync
            }
            attachedMap = map
            map.addOnCameraMoveStartedListener(listener)
        }

        onDispose {
            disposed = true
            attachedMap?.removeOnCameraMoveStartedListener(listener)
        }
    }
}

@Composable
private fun MapViewSurface(
    mapView: MapView,
    uiState: MapUiState,
    viewModel: MapViewModel,
    restrictedLocationMessage: String,
    onMapReady: (MapLibreMap) -> Unit
) {
    val currentUiState by rememberUpdatedState(uiState)
    val context = LocalContext.current

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    onMapReady(map)
                    configureMapUi(map)
                    configurePoiSelection(map, viewModel) { currentUiState }
                    configureMapClickHandling(
                        map = map,
                        viewModel = viewModel,
                        uiStateProvider = { currentUiState },
                        context = context,
                        restrictedLocationMessage = restrictedLocationMessage
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun configureMapUi(map: MapLibreMap) {
    map.uiSettings.isCompassEnabled = false
    map.uiSettings.isLogoEnabled = false
    map.uiSettings.isAttributionEnabled = false
}

private fun configurePoiSelection(
    map: MapLibreMap,
    viewModel: MapViewModel,
    uiStateProvider: () -> MapUiState
) {
    setLegacyMarkerClickListener(map) { markerSelection ->
        val uiState = uiStateProvider()

        val clickedIssue = markerSelection.snippet
            ?.toLongOrNull()
            ?.let { issueId -> uiState.issues.find { it.id == issueId } }
            ?: uiState.issues.find {
                it.coordinates.lat == markerSelection.position.latitude &&
                    it.coordinates.lon == markerSelection.position.longitude
            }

        if (clickedIssue != null) {
            viewModel.selectIssue(clickedIssue)
            return@setLegacyMarkerClickListener true
        }

        val selectedPoi = findSelectedPoi(
            puntsInteres = uiState.puntsInteres,
            markerSelection = markerSelection
        )
        viewModel.onPuntInteresSeleccionat(selectedPoi?.takeIf(PuntInteres::esEsdeveniment))

        selectedPoi != null
    }
}

private fun configureMapClickHandling(
    map: MapLibreMap,
    viewModel: MapViewModel,
    uiStateProvider: () -> MapUiState,
    context: Context,
    restrictedLocationMessage: String
) {
    map.addOnMapClickListener { point ->
        viewModel.onPuntInteresSeleccionat(null)
        if (viewModel.isInsideBarcelonaArea(point.latitude, point.longitude)){

            handleMapClick(
                map = map,
                point = point,
                uiState = uiStateProvider(),
                viewModel = viewModel,
                context = context
            )
        } else {
            Toast.makeText(
                context,
                restrictedLocationMessage,
                Toast.LENGTH_LONG
            ).show()
        }
        true

    }
}

private fun handleMapClick(
    map: MapLibreMap,
    point: LatLng,
    uiState: MapUiState,
    viewModel: MapViewModel,
    context: Context
) {
    if (uiState.modoRuta) {
        return
    }

    if (uiState.campActiu == textField.ORIGIN) {
        if (!viewModel.isInsideBarcelonaArea(point.latitude, point.longitude)) {
            Toast.makeText(context, "Aquest punt d'origen és fora de Barcelona", Toast.LENGTH_SHORT).show()
            return
        }
    } else if (uiState.campActiu == textField.DESTINY || uiState.campActiu == textField.NONE) {
        val origen = uiState.origenSeleccionado
            ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

        if (origen != null && !viewModel.isInsideBarcelonaArea(origen.latitude, origen.longitude)) {
            Toast.makeText(context, "No pots triar destí perquè el punt de sortida és fora de Barcelona", Toast.LENGTH_SHORT).show()
            return
        }
    }

    viewModel.onMapClicked(point)
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(point, 15.0),
        1000
    )
}

private fun processLocationPermissionResult(
    context: Context,
    permissions: Map<String, Boolean>,
    viewModel: MapViewModel,
    permissionNotificationTitle: String,
    locationPermissionRequiredMessage: String
) {
    val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    val granted = fine || coarse || hasLocationPermission(context)
    viewModel.onLocationPermissionsResult(granted)

    if (granted) {
        getBestLastKnownLocation(context)?.let(viewModel::updateLocation)
        return
    }

    ScreenNotificationManager.showNotification(
        notificationName = permissionNotificationTitle,
        text = locationPermissionRequiredMessage
    )
}

private fun resetMapToMainMenu(
    viewModel: MapViewModel,
    mapView: MapView,
    currentLocation: Location?,
    routeExitCameraSnapshot: RouteExitCameraSnapshot?
) {
    viewModel.clearRuta()
    viewModel.limpiarOrigen()
    disableNavigationCameraTracking(mapView)
    mapView.getMapAsync { map ->
        clearLegacyAnnotations(map)

        when {
            routeExitCameraSnapshot != null -> {
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(routeExitCameraSnapshot.toCameraPosition()),
                    1000
                )
            }
            currentLocation != null -> {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(currentLocation.latitude, currentLocation.longitude),
                        15.0
                    ),
                    1000
                )
            }
        }
    }
}

private fun processPrioritySelection(
    priority: RoutePriority,
    uiState: MapUiState,
    viewModel: MapViewModel,
    notificationTitle: String,
    waitingGpsLocationMessage: String
) {
    viewModel.onPrioritySelected(priority)

    val destination = uiState.destinoSeleccionado ?: return
    val originPoint = uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.toLatLng()
    if (originPoint == null) {
        ScreenNotificationManager.showNotification(
            notificationName = notificationTitle,
            text = waitingGpsLocationMessage
        )
        return
    }

    viewModel.calcularRuta(
        origenLong = originPoint.longitude,
        origenLat = originPoint.latitude,
        destiLong = destination.longitude,
        destiLat = destination.latitude
    )
}

private fun processAddressSelection(
    feature: Feature,
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView
) {
    val isSelectingOrigin = uiState.campActiu == textField.ORIGIN
    val context = mapView.context

    if (isSelectingOrigin) {
        if (!viewModel.isInsideBarcelonaArea(feature.geometry.latitud, feature.geometry.longitud)) {
            Toast.makeText(context, "L'adreÃ§a d'origen ha d'estar dins de Barcelona", Toast.LENGTH_LONG).show()
            return
        }
    } else {

        val latSortida: Double?
        val lonSortida: Double?

        if (uiState.origenSeleccionado != null) {
            latSortida = uiState.origenSeleccionado.latitude
            lonSortida = uiState.origenSeleccionado.longitude
        } else {
            latSortida = uiState.ultimaUbicacion?.latitude
            lonSortida = uiState.ultimaUbicacion?.longitude
        }

        if (latSortida != null && lonSortida != null) {
            if (!viewModel.isInsideBarcelonaArea(latSortida, lonSortida)) {
                Toast.makeText(context, "El punt de sortida actual estÃ  fora de Barcelona", Toast.LENGTH_LONG).show()
                return
            }
        }
    }

    viewModel.onAdrecaSeleccionada(feature)


    animateMapToPoint(
        mapView = mapView,
        point = LatLng(feature.geometry.latitud, feature.geometry.longitud)
    )
}

private fun handleMissingLocation(notificationTitle: String, searchingGpsSignalMessage: String) {
    ScreenNotificationManager.showNotification(
        notificationName = notificationTitle,
        text = searchingGpsSignalMessage
    )
}

private fun handleRouteModeRecenter(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    map: MapLibreMap?,
    currentLocation: Location?,
    navigationHeadingDegrees: Float?,
    onRouteExitCameraSnapshotCaptured: (RouteExitCameraSnapshot?) -> Unit
) {
    if ((uiState.usesLiveNavigation && !uiState.routeCompleted) || currentLocation != null) {
        onRouteExitCameraSnapshotCaptured(map?.cameraPosition?.toRouteExitCameraSnapshot())
    }

    if (uiState.usesLiveNavigation && !uiState.routeCompleted) {
        viewModel.resumeNavigationCameraTracking()
        enableNavigationCameraTracking(
            mapView = mapView,
            currentLocation = currentLocation,
            headingDegrees = navigationHeadingDegrees?.toDouble(),
            applyZoom = true
        )
    } else {
        activateLocationComponent(
            mapView = mapView,
            initialLocation = currentLocation
        )
    }
}

private fun recenterOnCurrentLocation(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    map: MapLibreMap?,
    requestLocationPermissions: () -> Unit,
    navigationHeadingDegrees: Float?,
    notificationTitle: String,
    searchingGpsSignalMessage: String,
    onRouteExitCameraSnapshotCaptured: (RouteExitCameraSnapshot?) -> Unit
) {
    if (!uiState.locationGranted) {
        requestLocationPermissions()
        return
    }

    val currentLocation = uiState.ultimaUbicacion

    if (uiState.modoRuta) {
        handleRouteModeRecenter(
            uiState = uiState,
            viewModel = viewModel,
            mapView = mapView,
            map = map,
            currentLocation = currentLocation,
            navigationHeadingDegrees = navigationHeadingDegrees,
            onRouteExitCameraSnapshotCaptured = onRouteExitCameraSnapshotCaptured
        )

        if (currentLocation == null) {
            handleMissingLocation(notificationTitle, searchingGpsSignalMessage)
        } else if (!uiState.usesLiveNavigation || uiState.routeCompleted) {
            centerMapOnLocation(mapView, currentLocation)
        }
        return
    }

    if (uiState.origenSeleccionado == null) {
        viewModel.limpiarOrigen()
    }

    activateLocationComponent(
        mapView = mapView,
        initialLocation = currentLocation
    )

    if (currentLocation == null) {
        handleMissingLocation(notificationTitle, searchingGpsSignalMessage)
        return
    }

    centerMapOnLocation(mapView, currentLocation)
}

private fun CameraPosition.toRouteExitCameraSnapshot(): RouteExitCameraSnapshot? {
    val target = target ?: return null

    return RouteExitCameraSnapshot(
        latitude = target.latitude,
        longitude = target.longitude,
        zoom = zoom,
        tilt = tilt,
        bearing = bearing
    )
}

private fun RouteExitCameraSnapshot.toCameraPosition(): CameraPosition {
    return CameraPosition.Builder()
        .target(LatLng(latitude, longitude))
        .zoom(zoom)
        .tilt(tilt)
        .bearing(bearing)
        .build()
}

private fun requestRouteCalculation(
    viewModel: MapViewModel,
    destination: LatLng?,
    selectedOrigin: LatLng?,
    currentLocation: Location?
) {
    val safeDestination = destination ?: return
    val originPoint = selectedOrigin ?: currentLocation?.toLatLng() ?: return

    viewModel.calcularRuta(
        origenLong = originPoint.longitude,
        origenLat = originPoint.latitude,
        destiLong = safeDestination.longitude,
        destiLat = safeDestination.latitude
    )
}

private fun renderMapStateAfterStyleLoaded(
    map: MapLibreMap,
    uiState: MapUiState,
    renderContext: MapRenderContext
) {
    renderContext.viewModel.onMapaListo()

    clearLegacyAnnotations(map)

    enableLocationOnMapIfNeeded(
        locationGranted = uiState.locationGranted,
        mapView = renderContext.mapView,
        currentLocation = uiState.ultimaUbicacion
    )
    syncNavigationCameraTracking(
        mapView = renderContext.mapView,
        modoRuta = uiState.modoRuta,
        routeCompleted = uiState.routeCompleted,
        navigationCameraFollowing = uiState.navigationCameraFollowing,
        currentLocation = uiState.ultimaUbicacion,
        headingDegrees = renderContext.navigationHeadingDegrees?.toDouble(),
        applyZoom = false
    )

    if (uiState.rutaCoordenades.isNotEmpty()) {
        drawCurrentRouteOnMap(
            map = map,
            uiState = uiState,
            context = renderContext.context,
            originLabel = renderContext.originLabel,
            destinationLabel = renderContext.destinationLabel
        )
        addPoiMarkersIfVisible(
            map = map,
            mostrarPuntsInteres = uiState.mostrarPuntsInteres,
            puntsInteres = uiState.puntsInteres,
            context = renderContext.context
        )
        addIssueMarkers(map, uiState.issues, renderContext.context)
        addEmergencyContactMarkers(
            map = map,
            emergencyContactLocations = uiState.emergencyContactLocations,
            pendingEmergencyContactLocation = uiState.pendingEmergencyContactLocation,
            context = renderContext.context,
            defaultTitle = renderContext.emergencyContactMarkerLabel
        )
        return
    }

    addSelectionMarkers(
        map = map,
        context = renderContext.context,
        origin = uiState.origenSeleccionado,
        destination = uiState.destinoSeleccionado,
        originLabel = renderContext.originLabel,
        destinationLabel = renderContext.destinationLabel
    )
    addIssueMarkers(map, uiState.issues, renderContext.context)
    addEmergencyContactMarkers(
        map = map,
        emergencyContactLocations = uiState.emergencyContactLocations,
        pendingEmergencyContactLocation = uiState.pendingEmergencyContactLocation,
        context = renderContext.context,
        defaultTitle = renderContext.emergencyContactMarkerLabel
    )
}

private fun addIssueMarkers(
    map: MapLibreMap,
    issues: List<IssueResponseDTO>,
    context: Context
) {
    issues.forEach { issue ->
        addLegacyMarker(
            map = map,
            position = LatLng(issue.coordinates.lat, issue.coordinates.lon),
            title = issue.type.name,
            icon = createIssueIcon(context, issue.type),
            snippet = issue.id.toString()
        )
    }
}

private fun addEmergencyContactMarkers(
    map: MapLibreMap,
    emergencyContactLocations: List<EmergencyContactLocation>,
    pendingEmergencyContactLocation: EmergencyContactLocation?,
    context: Context,
    defaultTitle: String
) {
    val highlightedLocationId = pendingEmergencyContactLocation?.id

    emergencyContactLocations.forEach { location ->
        addLegacyMarker(
            map = map,
            position = LatLng(location.latitude, location.longitude),
            title = location.username?.takeIf { it.isNotBlank() }
                ?: location.title?.takeIf { it.isNotBlank() }
                ?: defaultTitle,
            icon = createEmergencyContactIcon(
                context = context,
                highlighted = location.id == highlightedLocationId
            ),
            snippet = location.id
        )
    }
}

private fun enableLocationOnMapIfNeeded(
    locationGranted: Boolean,
    mapView: MapView,
    currentLocation: Location?
) {
    if (locationGranted) {
        activateLocationComponent(
            mapView = mapView,
            initialLocation = currentLocation
        )
    }
}

private fun syncNavigationCameraTracking(
    mapView: MapView,
    modoRuta: Boolean,
    routeCompleted: Boolean,
    navigationCameraFollowing: Boolean,
    currentLocation: Location?,
    headingDegrees: Double?,
    applyZoom: Boolean
) {
    if (modoRuta && !routeCompleted && navigationCameraFollowing) {
        enableNavigationCameraTracking(
            mapView = mapView,
            currentLocation = currentLocation,
            headingDegrees = headingDegrees,
            applyZoom = applyZoom
        )
    } else {
        disableNavigationCameraTracking(mapView)
    }
}

private fun drawCurrentRouteOnMap(
    map: MapLibreMap,
    uiState: MapUiState,
    context: Context,
    originLabel: String,
    destinationLabel: String
) {
    val routeOrigin = if (uiState.usesLiveNavigation) {
        uiState.ultimaUbicacion?.toLatLng() ?: uiState.origenSeleccionado
    } else {
        uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.toLatLng()
    }

    drawRouteOnMap(
        map = map,
        coordenades = uiState.rutaCoordenades,
        origen = routeOrigin,
        desti = uiState.destinoSeleccionado,
        context = context,
        originTitle = originLabel,
        destinationTitle = destinationLabel,
        animateCamera = !uiState.usesLiveNavigation,
        routeColor = uiState.routeColor
    )
}

private fun addPoiMarkersIfVisible(
    map: MapLibreMap,
    mostrarPuntsInteres: Boolean,
    puntsInteres: List<PuntInteres>,
    context: Context
) {
    if (!mostrarPuntsInteres) {
        return
    }

    visiblePoiMarkers(puntsInteres)
        .forEach { marker ->
            addLegacyMarker(
                map = map,
                position = marker.position,
                title = poiTitle(marker.punt),
                icon = crearIconaPoi(context, marker.punt.tipus),
                snippet = marker.punt.id
            )
        }
}

private data class VisiblePoiMarker(
    val punt: PuntInteres,
    val position: LatLng
)

private fun visiblePoiMarkers(puntsInteres: List<PuntInteres>): List<VisiblePoiMarker> {
    val groupedPois = puntsInteres
        .filter(::isVisiblePoi)
        .groupBy { punt ->
        "${punt.latitud};${punt.longitud}"
    }

    return groupedPois.values.flatMap { groupedPoints ->
        if (groupedPoints.size == 1) {
            val punt = groupedPoints.first()
            return@flatMap listOf(
                VisiblePoiMarker(
                    punt = punt,
                    position = LatLng(punt.latitud, punt.longitud)
                )
            )
        }

        groupedPoints.mapIndexed { index, punt ->
            VisiblePoiMarker(
                punt = punt,
                position = offsetPoiPosition(
                    punt = punt,
                    index = index,
                    total = groupedPoints.size
                )
            )
        }
    }
}

private fun offsetPoiPosition(
    punt: PuntInteres,
    index: Int,
    total: Int
): LatLng {
    val radius = 0.00014
    val angle = (2.0 * Math.PI * index) / total
    val latOffset = kotlin.math.sin(angle) * radius
    val lonOffset = kotlin.math.cos(angle) * radius

    return LatLng(
        punt.latitud + latOffset,
        punt.longitud + lonOffset
    )
}

private fun filterDisplayedPuntsInteres(
    puntsInteres: List<PuntInteres>,
    hideRouteEvents: Boolean
): List<PuntInteres> {
    if (!hideRouteEvents) {
        return puntsInteres
    }

    return puntsInteres.filterNot(PuntInteres::esEsdeveniment)
}

private fun resolveShouldHideRouteEvents(
    uiState: MapUiState,
    settings: MapEventSettings
): Boolean {
    if (
        settings.hideEventsDuringRoutePreview &&
        uiState.rutaCoordenades.isNotEmpty() &&
        !uiState.modoRuta
    ) {
        return true
    }

    if (settings.hideEventsDuringActiveRoute && uiState.modoRuta) {
        return true
    }

    return false
}

private fun isVisiblePoi(punt: PuntInteres): Boolean {
    val tipus = punt.tipus.trim().uppercase()
    return tipus == "FONT" ||
            tipus == "COMISSARIA" ||
            tipus == "CAMERA" ||
            tipus == "ESCALA_MECANICA" ||
            tipus == "REFUGI_CLIMATIC" ||
            tipus == "ESDEVENIMENT"
}

private fun poiTitle(punt: PuntInteres): String {
    return punt.nomMostrat()
}

private fun addSelectionMarkers(
    map: MapLibreMap,
    context: Context,
    origin: LatLng?,
    destination: LatLng?,
    originLabel: String,
    destinationLabel: String
) {
    origin?.let {
        addLegacyMarker(
            map = map,
            position = it,
            title = originLabel,
            icon = crearIconaGrisa(context)
        )
    }

    destination?.let {
        addLegacyMarker(
            map = map,
            position = it,
            title = destinationLabel
        )
    }
}

private fun startLocationListenerIfNeeded(
    locationGranted: Boolean,
    context: Context,
    mapView: MapView,
    viewModel: MapViewModel
): LocationListener? {
    if (!locationGranted) {
        return null
    }

    return startAndroidLocationUpdates(context, mapView) { location ->
        viewModel.updateLocation(location)
    }
}

private fun findSelectedPoi(
    puntsInteres: List<PuntInteres>,
    markerSelection: LegacyMarkerSelection
): PuntInteres? {
    markerSelection.snippet?.let { markerId ->
        puntsInteres.firstOrNull { punt -> punt.id == markerId }?.let { return it }
    }

    return puntsInteres.find { punt ->
        punt.latitud == markerSelection.position.latitude &&
            punt.longitud == markerSelection.position.longitude &&
            (
                markerSelection.title == null ||
                    poiTitle(punt) == markerSelection.title
                )
    }
}

private fun animateMapToPoint(
    mapView: MapView,
    point: LatLng
) {
    mapView.getMapAsync { map ->
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(point, 15.0),
            1500
        )
    }
}

private fun resolveMapStyleUrl(estiloSatelite: Boolean): String {
    return if (estiloSatelite) {
        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    } else {
        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    }
}

private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)



fun createIssueIcon(context: Context, tipus: IssueApiType): org.maplibre.android.annotations.Icon? {
    val (emoji, bgColor) = when (tipus) {
        IssueApiType.OBRES -> "🚧" to "#E67E22".toColorInt()
        IssueApiType.ACCESSIBILITAT -> "♿" to "#2B63D4".toColorInt()
        IssueApiType.SEGURETAT -> "🚨" to "#E74C3C".toColorInt()
        IssueApiType.ALTRES -> "⚠️" to "#F1C40F".toColorInt()
    }

    val size = 96
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.7f, shadowPaint)

    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, circlePaint)

    val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(emoji, size / 2f, y, textPaint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun createEmergencyContactIcon(
    context: Context,
    highlighted: Boolean
): org.maplibre.android.annotations.Icon? {
    val markerText = if (highlighted) "SOS" else "!"
    val backgroundColor = if (highlighted) "#B71C3B".toColorInt() else "#E76F51".toColorInt()
    val size = 96
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(48, 0, 0, 0)
        style = Paint.Style.FILL
    }
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = if (highlighted) 26f else 44f
        typeface = Typeface.DEFAULT_BOLD
    }

    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.7f, shadowPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, circlePaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, ringPaint)

    val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(markerText, size / 2f, textY, textPaint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}


private fun handleNewIssueConfirm(
    tipus: IssueType,
    ubicacio: String,
    descripcio: String,
    uiState: MapUiState,
    viewModel: MapViewModel,
    context: Context,
    textMevaUbicacio: String,
    textExit: String,
    outsideBarcelonaMessage: String
) {
    if (ubicacio == textMevaUbicacio && !isLocationInsideBarcelona(uiState, viewModel)) {
        Toast.makeText(context, outsideBarcelonaMessage, Toast.LENGTH_LONG).show()
        return
    }
    viewModel.reportIssue(
        tipus = tipus,
        adrecaText = ubicacio,
        descripcio = descripcio,
        textMevaUbicacio = textMevaUbicacio
    )
    viewModel.toggleMenuIncidencies(false)
    Toast.makeText(context, textExit, Toast.LENGTH_SHORT).show()
}

private fun isLocationInsideBarcelona(
    uiState: MapUiState,
    viewModel: MapViewModel
): Boolean {
    val loc = uiState.ultimaUbicacion ?: return false
    return viewModel.isInsideBarcelonaArea(loc.latitude, loc.longitude)
}

private suspend fun resolveIssueAddressOrFallback(
    coordinates: Coord,
    fallback: String
): String {
    return try {
        val response = com.safesteps.data.PhotonApi.service.reverseGeocode(
            lat = coordinates.lat,
            lon = coordinates.lon
        )
        response.features.firstOrNull()
            ?.properties?.getAddress()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    } catch (_: Exception) {
        fallback
    }
}

private fun issueTypeFromApi(apiType: IssueApiType): IssueType {
    return runCatching { IssueType.valueOf(apiType.name) }.getOrDefault(IssueType.OBRES)
}

data class PendingRoute(
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double
)
