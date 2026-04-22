package com.safesteps.map

import android.Manifest
import android.location.LocationListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.Feature
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.appString
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

@Composable
fun MapLibreScreen(
    modifier: Modifier = Modifier,
    currentUser: UserInfo? = null,
    currentLanguage: AppLanguage = AppLanguage.default,
    onLoginClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.applicationContext)
    )
    val mapView = rememberMapViewWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()

    val locationPermissionRequiredMessage = appString(R.string.location_permission_required)
    val waitingGpsLocationMessage = appString(R.string.waiting_gps_location)
    val searchingGpsSignalMessage = appString(R.string.searching_gps_signal)
    val originLabel = appString(R.string.origin_label)
    val destinationLabel = appString(R.string.destination_label)
    val hideExtraInfoLabel = appString(R.string.hide_extra_info)
    val showExtraInfoLabel = appString(R.string.show_extra_info)
    val standardMapStyleLabel = appString(R.string.map_style_standard)
    val satelliteMapStyleLabel = appString(R.string.map_style_satellite)
    val myLocationLabel = appString(R.string.my_location)
    val calculatingBestRouteLabel = appString(R.string.calculating_best_route)

    var floatingActionsBottomPadding by remember { mutableStateOf(16.dp) }

    LaunchedEffect(mapView) {
        viewModel.prepararNuevaSesionMapa()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fine || coarse || hasLocationPermission(context)
        viewModel.onLocationPermissionsResult(granted)

        if (granted) {
            getBestLastKnownLocation(context)?.let(viewModel::updateLocation)
        }

        if (!granted) {
            Toast.makeText(context, locationPermissionRequiredMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val requestLocationPermissions = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val resetToMainMenu = {
        viewModel.clearRuta()
        viewModel.limpiarOrigen()
        mapView.getMapAsync { map ->
            clearLegacyAnnotations(map)
        }
        uiState.ultimaUbicacion?.let { loc ->
            centerMapOnLocation(mapView, loc)
        }
        Unit
    }

    val handlePrioritySelection: (RoutePriority) -> Unit = { priority ->
        viewModel.onPrioritySelected(priority)
        val destination = uiState.destinoSeleccionado
        val selectedOrigin = uiState.origenSeleccionado
        val currentLocation = uiState.ultimaUbicacion

        if (destination != null) {
            val origenLong = selectedOrigin?.longitude ?: currentLocation?.longitude
            val origenLat = selectedOrigin?.latitude ?: currentLocation?.latitude

            if (origenLong != null && origenLat != null) {
                viewModel.calcularRuta(
                    origenLong = origenLong,
                    origenLat = origenLat,
                    destiLong = destination.longitude,
                    destiLat = destination.latitude
                )
            } else {
                Toast.makeText(context, waitingGpsLocationMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleAddressSelection: (Feature) -> Unit = { feature ->
        val wasSelectingOrigin = uiState.campActiu == textField.ORIGIN
        viewModel.onAdrecaSeleccionada(feature)
        if (wasSelectingOrigin) {
            val selectedPoint = LatLng(feature.geometry.latitud, feature.geometry.longitud)
            mapView.getMapAsync { map ->
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(selectedPoint, 15.0),
                    1500
                )
            }
        }
    }

    val centerOnCurrentLocation = {
        if (uiState.locationGranted) {
            viewModel.limpiarOrigen()
            activateLocationComponent(mapView)

            val currentLocation = uiState.ultimaUbicacion
            if (currentLocation != null) {
                centerMapOnLocation(mapView, currentLocation)
            } else {
                Toast.makeText(context, searchingGpsSignalMessage, Toast.LENGTH_SHORT).show()
            }
        } else {
            requestLocationPermissions()
        }
    }

    LaunchedEffect(currentLanguage) {
        viewModel.onLanguageChanged(currentLanguage)
    }

    LaunchedEffect(Unit) {
        val hasPermission = hasLocationPermission(context)
        viewModel.onLocationPermissionsResult(hasPermission)

        if (hasPermission) {
            getBestLastKnownLocation(context)?.let(viewModel::updateLocation)
        } else {
            requestLocationPermissions()
        }
    }

    LaunchedEffect(uiState.locationGranted, uiState.mapaListo) {
        if (uiState.locationGranted && uiState.mapaListo) {
            activateLocationComponent(mapView)
        }
    }

    LaunchedEffect(uiState.destinoSeleccionado, uiState.origenSeleccionado) {
        val destination = uiState.destinoSeleccionado
        if (destination != null) {
            val originPoint = uiState.origenSeleccionado
                ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

            if (originPoint != null) {
                viewModel.calcularRuta(
                    origenLong = originPoint.longitude,
                    origenLat = originPoint.latitude,
                    destiLong = destination.longitude,
                    destiLat = destination.latitude
                )
            }
        }
    }

    LaunchedEffect(
        uiState.estiloSatelite,
        uiState.modoRuta,
        uiState.rutaCoordenades,
        uiState.mostrarPuntsInteres,
        uiState.puntsInteres,
        uiState.origenSeleccionado,
        uiState.destinoSeleccionado
    ) {
        mapView.getMapAsync { map ->
            val styleUrl = if (uiState.estiloSatelite) {
                "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
            } else {
                "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
            }

            map.setStyle(styleUrl) {
                viewModel.onMapaListo()
                if (uiState.locationGranted) {
                    activateLocationComponent(mapView)
                }

                if (uiState.rutaCoordenades.isNotEmpty()) {
                    val originPoint = uiState.origenSeleccionado
                        ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

                    drawRoute(
                        mapView = mapView,
                        coordenades = uiState.rutaCoordenades,
                        origen = originPoint,
                        desti = uiState.destinoSeleccionado,
                        context = context,
                        originTitle = originLabel,
                        destinationTitle = destinationLabel
                    )

                    if (uiState.mostrarPuntsInteres) {
                        uiState.puntsInteres
                            .filter { punt ->
                                val tipus = punt.tipus.trim().uppercase()
                                tipus == "FONT" || tipus == "COMISSARIA"
                            }
                            .forEach { punt ->
                                val title = punt.nom ?: punt.tipus.lowercase()
                                    .replaceFirstChar { it.uppercase() }
                                addLegacyMarker(
                                    map = map,
                                    position = LatLng(punt.latitud, punt.longitud),
                                    title = title,
                                    icon = crearIconaPoi(context, punt.tipus)
                                )
                            }
                    }
                } else {
                    uiState.origenSeleccionado?.let { origin ->
                        addLegacyMarker(
                            map = map,
                            position = origin,
                            title = originLabel,
                            icon = crearIconaGrisa(context)
                        )
                    }

                    uiState.destinoSeleccionado?.let { destination ->
                        addLegacyMarker(
                            map = map,
                            position = destination,
                            title = destinationLabel
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(uiState.locationGranted) {
        var listener: LocationListener? = null
        if (uiState.locationGranted) {
            listener = startAndroidLocationUpdates(context, mapView) { location ->
                viewModel.updateLocation(location)
            }
        }

        onDispose {
            stopAndroidLocationUpdates(context, listener)
        }
    }

    LaunchedEffect(uiState.ultimaUbicacion, uiState.mapaListo) {
        val currentLocation = uiState.ultimaUbicacion
        if (currentLocation != null && uiState.mapaListo && !uiState.firstLocationZoomDone) {
            centerMapOnLocation(
                mapView = mapView,
                location = currentLocation,
                durationMs = 1500
            )
            viewModel.marcarZoomInicialHecho()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        setLegacyMarkerClickListener(map) { markerPosition ->
                            val selectedPoi = uiState.puntsInteres.find {
                                it.latitud == markerPosition.latitude &&
                                    it.longitud == markerPosition.longitude
                            }
                            viewModel.onPuntInteresSeleccionat(selectedPoi)
                            false
                        }

                        map.addOnMapClickListener { point ->
                            if (uiState.modoRuta) {
                                true
                            } else {
                                viewModel.onMapClicked(point)
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(point, 15.0),
                                    1000
                                )
                                true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
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
            onAdrecaSeleccionada = handleAddressSelection
        )

        RouteExperienceOverlay(
            uiState = uiState,
            showProfilePreferences = currentUser != null,
            onPrioritySelected = handlePrioritySelection,
            onStartRoute = { viewModel.iniciarNavegacio() },
            onClose = resetToMainMenu,
            onBottomPaddingChange = { padding ->
                floatingActionsBottomPadding = padding
            }
        )

        MapFloatingActions(
            uiState = uiState,
            bottomPadding = floatingActionsBottomPadding,
            hideExtraInfoLabel = hideExtraInfoLabel,
            showExtraInfoLabel = showExtraInfoLabel,
            standardMapStyleLabel = standardMapStyleLabel,
            satelliteMapStyleLabel = satelliteMapStyleLabel,
            myLocationLabel = myLocationLabel,
            onTogglePuntsInteres = { viewModel.togglePuntsInteres() },
            onToggleMapStyle = { viewModel.toggleEstiloSatelite() },
            onMyLocationClick = centerOnCurrentLocation
        )

        CalculatingRouteOverlay(
            visible = uiState.calculantRuta,
            calculatingBestRouteLabel = calculatingBestRouteLabel
        )
    }
}
