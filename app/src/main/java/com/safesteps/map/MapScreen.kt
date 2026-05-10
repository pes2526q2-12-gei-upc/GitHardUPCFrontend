package com.safesteps.map

import android.Manifest
import android.content.Context
import android.widget.Toast
import android.location.Location
import android.location.LocationListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.safesteps.data.PuntInteres
import com.safesteps.data.RouteCompletionResponse
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.appString
import org.maplibre.android.annotations.IconFactory
import com.safesteps.ui.notifications.ScreenNotificationManager
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.safesteps.data.IssueResponseDTO

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
    val reportIssueOutsideBarcelonaMessage: String,
    val issueLoadingAddressLabel: String,
    val issueLocationFallbackLabel: String,
    val mapLocationSelectionRestrictedMessage: String
)

private data class FloatingActionsLayout(
    val compactMode: Boolean,
    val showPoiAction: Boolean,
    val showMapStyleAction: Boolean,
    val showMyLocationAction: Boolean
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
    val destinationLabel: String
)

private data class NavigationTrackingState(
    val locationGranted: Boolean,
    val mapaListo: Boolean,
    val modoRuta: Boolean,
    val routeCompleted: Boolean,
    val navigationCameraFollowing: Boolean,
    val currentLocation: Location?
)

private data class MapScreenActions(
    val requestLocationPermissions: () -> Unit,
    val resetToMainMenu: () -> Unit,
    val onPrioritySelected: (RoutePriority) -> Unit,
    val onAddressSelected: (Feature) -> Unit,
    val onCenterCurrentLocation: () -> Unit
)

@Composable
private fun rememberMapScreenActions(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    context: Context,
    strings: MapScreenStrings,
    navigationHeadingDegrees: Float?
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
            currentLocation = uiState.ultimaUbicacion
        )
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
            requestLocationPermissions = requestLocationPermissions,
            navigationHeadingDegrees = navigationHeadingDegrees,
            notificationTitle = strings.mapNotificationTitle,
            searchingGpsSignalMessage = strings.searchingGpsSignalMessage
        )
    }
    return MapScreenActions(
        requestLocationPermissions = requestLocationPermissions,
        resetToMainMenu = resetToMainMenu,
        onPrioritySelected = onPrioritySelected,
        onAddressSelected = onAddressSelected,
        onCenterCurrentLocation = onCenterCurrentLocation
    )
}

@Composable
private fun MapScreenDialogs(
    uiState: MapUiState,
    viewModel: MapViewModel,
    currentUser: UserInfo?,
    strings: MapScreenStrings,
    context: Context,
    onLoginClick: () -> Unit
) {
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
            onConfirmar = { currentUser?.googleId?.let { viewModel.voteIssue(incidencia.id, true, it) } },
            onRebutjar = { currentUser?.googleId?.let { viewModel.voteIssue(incidencia.id, false, it) } },
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
}

@Composable
private fun MapScreenContent(
    modifier: Modifier,
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    currentUser: UserInfo?,
    strings: MapScreenStrings,
    actions: MapScreenActions,
    navigationHeadingDegrees: Float?,
    onLoginClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var floatingActionsBottomPadding by remember { mutableStateOf(16.dp) }
    var topOverlayHeightPx by remember { mutableFloatStateOf(0f) }
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
        val effectiveFloatingActionsBottomPadding = maxOf(
            floatingActionsBottomPadding,
            navigationBarsBottomPadding + 16.dp
        )
        val availableHeightPx = with(density) {
            maxHeight.toPx() - effectiveFloatingActionsBottomPadding.toPx() - topOverlayHeightPx
        }
        val floatingActionsLayout = resolveFloatingActionsLayout(
            freeHeightPx = availableHeightPx,
            density = density,
            hasPoiAction = uiState.puntsInteres.isNotEmpty()
        )

        MapViewSurface(
            mapView = mapView,
            uiState = uiState,
            viewModel = viewModel,
            restrictedLocationMessage = strings.mapLocationSelectionRestrictedMessage
        )

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
                onHeightChanged = { topOverlayHeightPx = it }
            )
        )

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
            onBottomPaddingChange = { padding ->
                floatingActionsBottomPadding = padding
            }
        )

        val isPlanningRoute = uiState.destinoSeleccionado != null && !uiState.modoRuta
        val isActiveRoute = uiState.modoRuta

        if (!isPlanningRoute && !isActiveRoute) {
            MapFloatingActions(
                uiState = uiState,
                state = FloatingActionsState(
                    bottomPadding = effectiveFloatingActionsBottomPadding,
                    compactMode = floatingActionsLayout.compactMode,
                    showPoiAction = floatingActionsLayout.showPoiAction,
                    showMapStyleAction = floatingActionsLayout.showMapStyleAction,
                    showMyLocationAction = floatingActionsLayout.showMyLocationAction
                ),
                labels = FloatingActionLabels(
                    hideExtraInfoLabel = strings.hideExtraInfoLabel,
                    showExtraInfoLabel = strings.showExtraInfoLabel,
                    standardMapStyleLabel = strings.standardMapStyleLabel,
                    satelliteMapStyleLabel = strings.satelliteMapStyleLabel,
                    myLocationLabel = strings.myLocationLabel
                ),
                callbacks = FloatingActionCallbacks(
                    onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
                    onToggleMapStyle = { viewModel.toggleEstiloSatelite() },
                    onMyLocationClick = actions.onCenterCurrentLocation
                ),
                reportIssueLabel = strings.reportIssueLabel,
                onReportIssueClick = { viewModel.toggleMenuIncidencies(true) }
            )
        }

        if (isPlanningRoute || isActiveRoute) {
            RouteModeFloatingActions(
                uiState = uiState,
                bottomPadding = effectiveFloatingActionsBottomPadding,
                onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
                onToggleMapStyle = { viewModel.toggleEstiloSatelite() },
                onMyLocationClick = actions.onCenterCurrentLocation,
                onReportIssueClick = { viewModel.toggleMenuIncidencies(true) },
                hideExtraInfoLabel = strings.hideExtraInfoLabel,
                showExtraInfoLabel = strings.showExtraInfoLabel,
                standardMapStyleLabel = strings.standardMapStyleLabel,
                satelliteMapStyleLabel = strings.satelliteMapStyleLabel,
                myLocationLabel = strings.myLocationLabel,
                reportIssueLabel = strings.reportIssueLabel
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
            onLoginClick = onLoginClick
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
    onRouteCompleted: (RouteCompletionResponse) -> Unit = {}
) {

    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.applicationContext)
    )
    val mapView = rememberMapViewWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()
    val routeCompletionResult = viewModel.routeResult
    var showLevelUpOverlay by remember { mutableStateOf(false) }
    var levelUpLevel by remember { mutableStateOf(1L) }

    LaunchedEffect(routeCompletionResult) {
        if (routeCompletionResult != null) {
            onRouteCompleted(routeCompletionResult)
            if (routeCompletionResult.levelUpdated == true) {
                levelUpLevel = routeCompletionResult.level ?: 1
                showLevelUpOverlay = true
            }
            viewModel.dismissRouteResult()
        }
    }
    val strings = mapScreenStrings()
    var navigationHeadingDegrees by remember { mutableStateOf<Float?>(null) }

    val actions = rememberMapScreenActions(
        uiState = uiState,
        viewModel = viewModel,
        mapView = mapView,
        context = context,
        strings = strings,
        navigationHeadingDegrees = navigationHeadingDegrees
    )

    val renderContext = MapRenderContext(
        mapView = mapView,
        context = context,
        viewModel = viewModel,
        navigationHeadingDegrees = navigationHeadingDegrees,
        originLabel = strings.originLabel,
        destinationLabel = strings.destinationLabel
    )

    LaunchedEffect(currentUser?.googleId) {
        viewModel.onCurrentUserChanged(currentUser)
    }

    LaunchedEffect(issuesRefreshTrigger) {
        viewModel.loadIssuesMap()
    }

    MapScreenEffects(
        currentLanguage = currentLanguage,
        uiState = uiState,
        renderContext = renderContext,
        mapNotificationTitle = strings.mapNotificationTitle,
        callbacks = MapScreenEffectCallbacks(
            requestLocationPermissions = actions.requestLocationPermissions,
            onNavigationHeadingChanged = { navigationHeadingDegrees = it }
        )
    )

    MapScreenContent(
        modifier = modifier,
        uiState = uiState,
        viewModel = viewModel,
        mapView = mapView,
        currentUser = currentUser,
        strings = strings,
        actions = actions,
        navigationHeadingDegrees = navigationHeadingDegrees,
        onLoginClick = onLoginClick,
        onMenuClick = onMenuClick,
        onProfileClick = onProfileClick
    )

    if (showLevelUpOverlay) {
        com.safesteps.profile.LevelUpAnimationOverlay(
            level = levelUpLevel,
            onDismiss = { showLevelUpOverlay = false }
        )
    }
}

@Composable
private fun BoxScope.RouteModeFloatingActions(
    uiState: MapUiState,
    bottomPadding: Dp,
    onTogglePuntsInteres: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onMyLocationClick: () -> Unit,
    onReportIssueClick: () -> Unit,
    hideExtraInfoLabel: String,
    showExtraInfoLabel: String,
    standardMapStyleLabel: String,
    satelliteMapStyleLabel: String,
    myLocationLabel: String,
    reportIssueLabel: String
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
            onClick = onToggleMapStyle,
            contentDescription = if (uiState.estiloSatelite) standardMapStyleLabel else satelliteMapStyleLabel,
            icon = Icons.Default.Layers,
            tint = if (uiState.estiloSatelite) Color(0xFF2A5F8A) else Color(0xFF33413B),
            testTagId = "btn_satellit"
        )
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
    }
}

@Composable
private fun CompactCircularMapAction(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    tint: Color = Color(0xFF33413B),
    backgroundColor: Color = Color.White,
    testTagId: String? = null
) {
    Surface(
        shape = CircleShape,
        modifier = Modifier.size(44.dp),
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
        reportIssueOutsideBarcelonaMessage = appString(R.string.issue_report_outside_barcelona),
        issueLoadingAddressLabel = appString(R.string.issue_loading_address),
        issueLocationFallbackLabel = appString(R.string.issue_location_fallback),
        mapLocationSelectionRestrictedMessage = appString(R.string.map_location_selection_restricted)
    )
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
        uiState.issues
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
    hasPoiAction: Boolean
): FloatingActionsLayout {
    val safeFreeHeightPx = (freeHeightPx - with(density) { 24.dp.toPx() }).coerceAtLeast(0f)
    val layoutCandidates = floatingActionsLayoutCandidates(hasPoiAction)

    return layoutCandidates.firstOrNull { layout ->
        safeFreeHeightPx >= with(density) { estimatedFloatingActionsHeight(layout).toPx() }
    } ?: layoutCandidates.last()
}

private fun floatingActionsLayoutCandidates(hasPoiAction: Boolean): List<FloatingActionsLayout> {
    return listOf(
        FloatingActionsLayout(
            compactMode = false,
            showPoiAction = hasPoiAction,
            showMapStyleAction = true,
            showMyLocationAction = true
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = hasPoiAction,
            showMapStyleAction = true,
            showMyLocationAction = true
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = true,
            showMyLocationAction = true
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = false,
            showMyLocationAction = true
        ),
        FloatingActionsLayout(
            compactMode = true,
            showPoiAction = false,
            showMapStyleAction = false,
            showMyLocationAction = false
        )
    )
}

private fun estimatedFloatingActionsHeight(layout: FloatingActionsLayout): Dp {
    val itemHeights = listOfNotNull(
        floatingActionHeightOrNull(layout.showPoiAction, layout.compactMode, compactHeight = 34.dp, expandedHeight = 40.dp),
        floatingActionHeightOrNull(layout.showMapStyleAction, layout.compactMode, compactHeight = 34.dp, expandedHeight = 40.dp),
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
    restrictedLocationMessage: String
) {
    val currentUiState by rememberUpdatedState(uiState)
    val context = LocalContext.current

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
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
    setLegacyMarkerClickListener(map) { markerPosition ->
        val uiState = uiStateProvider()

        val clickedIssue = uiState.issues.find {
            it.coordinates.lat == markerPosition.latitude &&
                    it.coordinates.lon == markerPosition.longitude
        }

        if (clickedIssue != null) {
            viewModel.selectIssue(clickedIssue)
            return@setLegacyMarkerClickListener true
        }

        val selectedPoi = findSelectedPoi(
            puntsInteres = uiState.puntsInteres,
            markerPosition = markerPosition
        )
        viewModel.onPuntInteresSeleccionat(selectedPoi)

        false
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
    currentLocation: Location?
) {
    viewModel.clearRuta()
    viewModel.limpiarOrigen()
    disableNavigationCameraTracking(mapView)
    mapView.getMapAsync { map ->
        clearLegacyAnnotations(map)
    }
    currentLocation?.let { location ->
        centerMapOnLocation(mapView, location)
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

private fun recenterOnCurrentLocation(
    uiState: MapUiState,
    viewModel: MapViewModel,
    mapView: MapView,
    requestLocationPermissions: () -> Unit,
    navigationHeadingDegrees: Float?,
    notificationTitle: String,
    searchingGpsSignalMessage: String
) {
    if (!uiState.locationGranted) {
        requestLocationPermissions()
        return
    }

    val currentLocation = uiState.ultimaUbicacion

    if (uiState.modoRuta) {
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

        if (currentLocation == null) {
            ScreenNotificationManager.showNotification(
                notificationName = notificationTitle,
                text = searchingGpsSignalMessage
            )
        } else if (!uiState.usesLiveNavigation || uiState.routeCompleted) {
            centerMapOnLocation(mapView, currentLocation)
        }
        return
    }

    // If the user has confirmed a custom origin (e.g. during route planning),
    // pressing my-location must NOT replace that origin. Just zoom on the
    // current location and leave the route/origin untouched.
    if (uiState.origenSeleccionado != null) {
        activateLocationComponent(
            mapView = mapView,
            initialLocation = currentLocation
        )

        if (currentLocation == null) {
            ScreenNotificationManager.showNotification(
                notificationName = notificationTitle,
                text = searchingGpsSignalMessage
            )
            return
        }

        centerMapOnLocation(mapView, currentLocation)
        return
    }

    viewModel.limpiarOrigen()
    activateLocationComponent(
        mapView = mapView,
        initialLocation = currentLocation
    )

    if (currentLocation == null) {
        ScreenNotificationManager.showNotification(
            notificationName = notificationTitle,
            text = searchingGpsSignalMessage
        )
        return
    }

    centerMapOnLocation(mapView, currentLocation)
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
            icon = createIssueIcon(context, issue.type)
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

    puntsInteres
        .filter(::isVisiblePoi)
        .forEach { punt ->
            addLegacyMarker(
                map = map,
                position = LatLng(punt.latitud, punt.longitud),
                title = poiTitle(punt),
                icon = crearIconaPoi(context, punt.tipus)
            )
        }
}

private fun isVisiblePoi(punt: PuntInteres): Boolean {
    val tipus = punt.tipus.trim().uppercase()
    return tipus == "FONT" || tipus == "COMISSARIA"
}

private fun poiTitle(punt: PuntInteres): String {
    return punt.nom ?: punt.tipus.lowercase().replaceFirstChar { char ->
        char.uppercase()
    }
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
    markerPosition: LatLng
): PuntInteres? {
    return puntsInteres.find { punt ->
        punt.latitud == markerPosition.latitude &&
                punt.longitud == markerPosition.longitude
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