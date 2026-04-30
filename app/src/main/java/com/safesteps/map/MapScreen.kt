package com.safesteps.map

import android.Manifest
import android.content.Context
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.appString
import org.maplibre.android.annotations.IconFactory
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
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.safesteps.data.IssueResponseDTO

private data class MapScreenStrings(
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

@Composable
fun MapLibreScreen(
    modifier: Modifier = Modifier,
    currentUser: UserInfo? = null,
    currentLanguage: AppLanguage = AppLanguage.default,
    issuesRefreshTrigger: Int = 0,
    onLoginClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.applicationContext)
    )
    val mapView = rememberMapViewWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()
    val strings = mapScreenStrings()
    var navigationHeadingDegrees by remember { mutableStateOf<Float?>(null) }

    var floatingActionsBottomPadding by remember { mutableStateOf(16.dp) }

    val requestLocationPermissions = rememberLocationPermissionRequester(
        context = context,
        viewModel = viewModel,
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
            context = context,
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
            context = context,
            requestLocationPermissions = requestLocationPermissions,
            navigationHeadingDegrees = navigationHeadingDegrees,
            searchingGpsSignalMessage = strings.searchingGpsSignalMessage
        )
    }

    LaunchedEffect(currentUser?.googleId) {
        viewModel.onCurrentUserChanged(currentUser)
    }

    LaunchedEffect(issuesRefreshTrigger) {
        viewModel.loadIssuesMap()
    }

    MapScreenEffects(
        mapView = mapView,
        currentLanguage = currentLanguage,
        uiState = uiState,
        context = context,
        viewModel = viewModel,
        requestLocationPermissions = requestLocationPermissions,
        navigationHeadingDegrees = navigationHeadingDegrees,
        onNavigationHeadingChanged = { navigationHeadingDegrees = it },
        originLabel = strings.originLabel,
        destinationLabel = strings.destinationLabel
    )

    Box(modifier = modifier.fillMaxSize()) {
        MapViewSurface(
            mapView = mapView,
            uiState = uiState,
            viewModel = viewModel,
            restrictedLocationMessage = strings.mapLocationSelectionRestrictedMessage
        )

        MainMapOverlay(
            uiState = uiState,
            currentUser = currentUser,
            onLoginClick = onLoginClick,
            onProfileClick = onProfileClick,
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
            onAdrecaSeleccionada = onAddressSelected
        )

        RouteExperienceOverlay(
            uiState = uiState,
            showProfilePreferences = !currentUser?.googleId.isNullOrBlank(),
            onPrioritySelected = onPrioritySelected,
            onStartRoute = {
                viewModel.iniciarNavegacio()
                uiState.ultimaUbicacion?.let { location ->
                    enableNavigationCameraTracking(
                        mapView = mapView,
                        currentLocation = location,
                        headingDegrees = navigationHeadingDegrees?.toDouble(),
                        applyZoom = true
                    )
                }
            },
            onClose = resetToMainMenu,
            onBottomPaddingChange = { padding ->
                floatingActionsBottomPadding = padding
            }
        )

        MapFloatingActions(
            uiState = uiState,
            bottomPadding = floatingActionsBottomPadding,
            hideExtraInfoLabel = strings.hideExtraInfoLabel,
            showExtraInfoLabel = strings.showExtraInfoLabel,
            standardMapStyleLabel = strings.standardMapStyleLabel,
            satelliteMapStyleLabel = strings.satelliteMapStyleLabel,
            myLocationLabel = strings.myLocationLabel,
            onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
            onToggleMapStyle = { viewModel.toggleEstiloSatelite() },
            onMyLocationClick = onCenterCurrentLocation,
            reportIssueLabel = strings.reportIssueLabel,
            onReportIssueClick = { viewModel.toggleMenuIncidencies(true) }
        )

        CalculatingRouteOverlay(
            visible = uiState.calculantRuta,
            calculatingBestRouteLabel = strings.calculatingBestRouteLabel
        )

        val textMevaUbicacio = appString(R.string.my_location)
        val textExit = context.getString(R.string.report_registered)

        ReportIssueDialog(
            visible = uiState.mostrarIncidencies,
            isLoggedIn = currentUser != null,
            defaultLocationText = textMevaUbicacio,
            onDismiss = {
                viewModel.toggleMenuIncidencies(false)
            },
            onConfirm = { tipus, ubicacio, descripcio, coord ->
                if (ubicacio == textMevaUbicacio) {
                    val loc = uiState.ultimaUbicacion
                    if (loc == null || !viewModel.isInsideBarcelonaArea(loc.latitude, loc.longitude)) {
                        Toast.makeText(
                            context,
                            strings.reportIssueOutsideBarcelonaMessage,
                            Toast.LENGTH_LONG
                        ).show()
                        return@ReportIssueDialog
                    }
                }
                viewModel.reportIssue(
                    tipus = tipus,
                    adrecaText = ubicacio,
                    descripcio = descripcio,
                    textMevaUbicacio = textMevaUbicacio
                )

                viewModel.toggleMenuIncidencies(false)
                Toast.makeText(context, textExit, Toast.LENGTH_SHORT).show()
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
                miVot = miVot,
                onDismiss = { viewModel.selectIssue(null) },
                userVote = uiState.userVotes[incidencia.id],
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
                try {
                    val response = com.safesteps.data.PhotonApi.service.reverseGeocode(
                        lat = incidencia.coordinates.lat,
                        lon = incidencia.coordinates.lon
                    )

                    val feature = response.features.firstOrNull()
                    if (feature != null) {
                        val adreca = feature.properties.getAddress()
                        adrecaTransformada = adreca.ifBlank { strings.issueLocationFallbackLabel }
                    } else {
                        adrecaTransformada = strings.issueLocationFallbackLabel
                    }
                } catch (_: Exception) {
                    adrecaTransformada = strings.issueLocationFallbackLabel
                }
            }

            ReportIssueDialog(
                visible = true,
                isLoggedIn = currentUser != null,
                isEditMode = true,
                defaultLocationText = adrecaTransformada,
                initialType = try { IssueType.valueOf(incidencia.type.name) } catch (e: Exception) { IssueType.OBRES },
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
}

@Composable
private fun mapScreenStrings(): MapScreenStrings {
    return MapScreenStrings(
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
    locationPermissionRequiredMessage: String
): () -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        processLocationPermissionResult(
            context = context,
            permissions = permissions,
            viewModel = viewModel,
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
    mapView: MapView,
    currentLanguage: AppLanguage,
    uiState: MapUiState,
    context: Context,
    viewModel: MapViewModel,
    requestLocationPermissions: () -> Unit,
    navigationHeadingDegrees: Float?,
    onNavigationHeadingChanged: (Float?) -> Unit,
    originLabel: String,
    destinationLabel: String
) {
    PrepareMapSessionEffect(
        mapView = mapView,
        viewModel = viewModel
    )
    LanguageChangeEffect(
        currentLanguage = currentLanguage,
        viewModel = viewModel
    )
    InitialLocationPermissionEffect(
        context = context,
        viewModel = viewModel,
        requestLocationPermissions = requestLocationPermissions
    )
    LocationComponentActivationEffect(
        locationGranted = uiState.locationGranted,
        mapaListo = uiState.mapaListo,
        ultimaUbicacion = uiState.ultimaUbicacion,
        mapView = mapView
    )
    NavigationHeadingSensorEffect(
        context = context,
        enabled = uiState.modoRuta && !uiState.routeCompleted && uiState.navigationCameraFollowing,
        onHeadingChanged = onNavigationHeadingChanged
    )
    NavigationCameraTrackingEffect(
        locationGranted = uiState.locationGranted,
        mapaListo = uiState.mapaListo,
        modoRuta = uiState.modoRuta,
        routeCompleted = uiState.routeCompleted,
        navigationCameraFollowing = uiState.navigationCameraFollowing,
        currentLocation = uiState.ultimaUbicacion,
        headingDegrees = navigationHeadingDegrees,
        mapView = mapView,
    )
    NavigationCameraGestureDismissEffect(
        mapView = mapView,
        uiState = uiState,
        viewModel = viewModel
    )
    RouteRecalculationEffect(
        destinoSeleccionado = uiState.destinoSeleccionado,
        origenSeleccionado = uiState.origenSeleccionado,
        ultimaUbicacion = uiState.ultimaUbicacion,
        viewModel = viewModel
    )
    NavigationNoticeEffect(
        navigationNotice = uiState.navigationNotice,
        context = context,
        viewModel = viewModel
    )
    MapStyleRenderingEffect(
        mapView = mapView,
        uiState = uiState,
        context = context,
        viewModel = viewModel,
        navigationHeadingDegrees = navigationHeadingDegrees,
        originLabel = originLabel,
        destinationLabel = destinationLabel
    )
    LocationUpdatesEffect(
        locationGranted = uiState.locationGranted,
        context = context,
        mapView = mapView,
        viewModel = viewModel
    )
    InitialZoomEffect(
        ultimaUbicacion = uiState.ultimaUbicacion,
        mapaListo = uiState.mapaListo,
        firstLocationZoomDone = uiState.firstLocationZoomDone,
        mapView = mapView,
        viewModel = viewModel
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
    context: Context,
    viewModel: MapViewModel
) {
    LaunchedEffect(navigationNotice) {
        if (navigationNotice.isNullOrBlank()) {
            return@LaunchedEffect
        }

        Toast.makeText(context, navigationNotice, Toast.LENGTH_SHORT).show()
        viewModel.onNavigationNoticeConsumed()
    }
}

@Composable
private fun MapStyleRenderingEffect(
    mapView: MapView,
    uiState: MapUiState,
    context: Context,
    viewModel: MapViewModel,
    navigationHeadingDegrees: Float?,
    originLabel: String,
    destinationLabel: String
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
        mapView.getMapAsync { map ->
            map.setStyle(resolveMapStyleUrl(uiState.estiloSatelite)) {
                renderMapStateAfterStyleLoaded(
                    map = map,
                    mapView = mapView,
                    uiState = uiState,
                    context = context,
                    viewModel = viewModel,
                    navigationHeadingDegrees = navigationHeadingDegrees,
                    originLabel = originLabel,
                    destinationLabel = destinationLabel
                )
            }

            map.getStyle { _ ->
                map.clear()

                uiState.issues.forEach { issue ->
                    val position = LatLng(issue.coordinates.lat, issue.coordinates.lon)
                    addLegacyMarker(
                        map = map,
                        position = position,
                        title = issue.type.name,
                        icon = createIssueIcon(context, issue.type)
                    )
                }
            }
        }
    }
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
    locationGranted: Boolean,
    mapaListo: Boolean,
    modoRuta: Boolean,
    routeCompleted: Boolean,
    navigationCameraFollowing: Boolean,
    currentLocation: Location?,
    headingDegrees: Float?,
    mapView: MapView
) {
    LaunchedEffect(
        locationGranted,
        mapaListo,
        modoRuta,
        routeCompleted,
        navigationCameraFollowing,
        currentLocation?.latitude,
        currentLocation?.longitude,
        currentLocation?.bearing,
        headingDegrees?.toInt()
    ) {
        if (!locationGranted || !mapaListo) {
            return@LaunchedEffect
        }

        syncNavigationCameraTracking(
            mapView = mapView,
            modoRuta = modoRuta,
            routeCompleted = routeCompleted,
            navigationCameraFollowing = navigationCameraFollowing,
            currentLocation = currentLocation,
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
): Boolean {
    if (uiState.modoRuta) {
        return true
    }

    if (uiState.campActiu == textField.ORIGIN) {
        if (!viewModel.isInsideBarcelonaArea(point.latitude, point.longitude)) {
            Toast.makeText(context, "Aquest punt d'origen Ã©s fora de Barcelona", Toast.LENGTH_SHORT).show()
            return true
        }
    } else if (uiState.campActiu == textField.DESTINY || uiState.campActiu == textField.NONE) {
        val origen = uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

        if (origen != null && !viewModel.isInsideBarcelonaArea(origen.latitude, origen.longitude)) {
            Toast.makeText(context, "No pots triar destÃ­ perquÃ¨ el punt de sortida Ã©s fora de Barcelona", Toast.LENGTH_SHORT).show()
            return true
        }
    }

    viewModel.onMapClicked(point)
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(point, 15.0),
        1000
    )
    return true
}

private fun processLocationPermissionResult(
    context: Context,
    permissions: Map<String, Boolean>,
    viewModel: MapViewModel,
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

    Toast.makeText(context, locationPermissionRequiredMessage, Toast.LENGTH_SHORT).show()
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
    context: Context,
    waitingGpsLocationMessage: String
) {
    viewModel.onPrioritySelected(priority)

    val destination = uiState.destinoSeleccionado ?: return
    val originPoint = uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.toLatLng()
    if (originPoint == null) {
        Toast.makeText(context, waitingGpsLocationMessage, Toast.LENGTH_SHORT).show()
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
    context: Context,
    requestLocationPermissions: () -> Unit,
    navigationHeadingDegrees: Float?,
    searchingGpsSignalMessage: String
) {
    if (!uiState.locationGranted) {
        requestLocationPermissions()
        return
    }

    val currentLocation = uiState.ultimaUbicacion

    if (uiState.modoRuta) {
        if (!uiState.routeCompleted) {
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
            Toast.makeText(context, searchingGpsSignalMessage, Toast.LENGTH_SHORT).show()
        } else if (uiState.routeCompleted) {
            centerMapOnLocation(mapView, currentLocation)
        }
        return
    }

    viewModel.limpiarOrigen()
    activateLocationComponent(
        mapView = mapView,
        initialLocation = currentLocation
    )

    if (currentLocation == null) {
        Toast.makeText(context, searchingGpsSignalMessage, Toast.LENGTH_SHORT).show()
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
    mapView: MapView,
    uiState: MapUiState,
    context: Context,
    viewModel: MapViewModel,
    navigationHeadingDegrees: Float?,
    originLabel: String,
    destinationLabel: String
) {
    viewModel.onMapaListo()
    enableLocationOnMapIfNeeded(
        locationGranted = uiState.locationGranted,
        mapView = mapView,
        currentLocation = uiState.ultimaUbicacion
    )
    syncNavigationCameraTracking(
        mapView = mapView,
        modoRuta = uiState.modoRuta,
        routeCompleted = uiState.routeCompleted,
        navigationCameraFollowing = uiState.navigationCameraFollowing,
        currentLocation = uiState.ultimaUbicacion,
        headingDegrees = navigationHeadingDegrees?.toDouble(),
        applyZoom = false
    )

    if (uiState.rutaCoordenades.isNotEmpty()) {
        drawCurrentRoute(
            mapView = mapView,
            uiState = uiState,
            context = context,
            originLabel = originLabel,
            destinationLabel = destinationLabel
        )
        addPoiMarkersIfVisible(
            map = map,
            mostrarPuntsInteres = uiState.mostrarPuntsInteres,
            puntsInteres = uiState.puntsInteres,
            context = context
        )
        return
    }

    addSelectionMarkers(
        map = map,
        context = context,
        origin = uiState.origenSeleccionado,
        destination = uiState.destinoSeleccionado,
        originLabel = originLabel,
        destinationLabel = destinationLabel
    )
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

private fun drawCurrentRoute(
    mapView: MapView,
    uiState: MapUiState,
    context: Context,
    originLabel: String,
    destinationLabel: String
) {
    val routeOrigin = if (uiState.modoRuta) {
        uiState.ultimaUbicacion?.toLatLng() ?: uiState.origenSeleccionado
    } else {
        uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.toLatLng()
    }

    drawRoute(
        mapView = mapView,
        coordenades = uiState.rutaCoordenades,
        origen = routeOrigin,
        desti = uiState.destinoSeleccionado,
        context = context,
        originTitle = originLabel,
        destinationTitle = destinationLabel
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










