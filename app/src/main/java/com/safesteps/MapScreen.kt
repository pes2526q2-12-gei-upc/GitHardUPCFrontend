package com.safesteps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private enum class RoutePriority {
    SAFETY,
    ACCESSIBILITY,
    HEAT
}

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasCoarseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasLocationPermission(context: Context): Boolean {
    return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return mapView
}

private fun activateLocationComponent(mapView: MapView) {
    mapView.getMapAsync { map ->
        val context = mapView.context
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return@getMapAsync
        }

        val style = map.style ?: return@getMapAsync
        val locationComponent = map.locationComponent

        if (!locationComponent.isLocationComponentActivated) {
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor(AndroidColor.parseColor("#1E88E5"))
                .backgroundTintColor(AndroidColor.WHITE)
                .bearingTintColor(AndroidColor.parseColor("#1E88E5"))
                .accuracyColor(AndroidColor.parseColor("#5533B5E5"))
                .pulseEnabled(true)
                .pulseColor(AndroidColor.parseColor("#8833B5E5"))
                .build()

            val activationOptions = LocationComponentActivationOptions
                .builder(context, style)
                .useDefaultLocationEngine(false)
                .locationComponentOptions(options)
                .build()

            locationComponent.activateLocationComponent(activationOptions)
        }

        try {
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.zoomWhileTracking(15.0)
        } catch (_: SecurityException) {
        }
    }
}

private fun pushLocationToMap(mapView: MapView, location: Location) {
    mapView.getMapAsync { map ->
        val locationComponent = map.locationComponent
        if (locationComponent.isLocationComponentActivated) {
            locationComponent.forceLocationUpdate(location)
        }
    }
}

private fun startAndroidLocationUpdates(
    context: Context,
    mapView: MapView,
    onLocationUpdated: (Location) -> Unit
): LocationListener? {
    if (!hasLocationPermission(context)) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { location ->
        pushLocationToMap(mapView, location)
        onLocationUpdated(location)
    }

    val fineGranted = hasFineLocationPermission(context)
    val coarseGranted = hasCoarseLocationPermission(context)

    val providers = buildList {
        if (fineGranted) add(LocationManager.GPS_PROVIDER)
        if (fineGranted || coarseGranted) add(LocationManager.NETWORK_PROVIDER)
    }

    for (provider in providers) {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 1000L, 1f, listener)
                locationManager.getLastKnownLocation(provider)?.let {
                    pushLocationToMap(mapView, it)
                    onLocationUpdated(it)
                }
            }
        } catch (_: SecurityException) {
        }
    }

    return listener
}

private fun stopAndroidLocationUpdates(context: Context, listener: LocationListener?) {
    if (listener == null) return
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    locationManager.removeUpdates(listener)
}

private fun calculateDistanceAndDuration(
    currentLocation: Location?,
    destination: LatLng?
): Pair<String, String> {
    if (currentLocation == null || destination == null) return "-- km" to "-- min"

    val destinationLocation = Location("destination").apply {
        latitude = destination.latitude
        longitude = destination.longitude
    }

    val meters = currentLocation.distanceTo(destinationLocation)
    val km = meters / 1000.0
    val minutes = max(1, (km * 12.0).roundToInt())

    val distanceText = if (km < 1.0) {
        "${meters.roundToInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", km)
    }

    return distanceText to "$minutes min"
}

@Composable
private fun SearchBarItem(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFFE4ECE8),
    onFocus: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(26.dp)
            )
            .background(Color.White, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            leadingIcon()

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF9AA7A0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF3D4A45)
                    ),
                    cursorBrush = SolidColor(Color(0xFF5AC98B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) {
                                onFocus?.invoke()
                            }
                        }
                )
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailingContent()
            }
        }
    }
}

@Composable
private fun TopSearchPanel(
    origen: String,
    onOrigenChange: (String) -> Unit,
    destino: String,
    onDestinoChange: (String) -> Unit,
    mostrarOrigen: Boolean,
    onDestinoFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = Color(0xFFF4F6F5)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú",
                            tint = Color(0xFF66716C),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "SafeSteps",
                    color = Color(0xFF33413B),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color(0xFF6DD29A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = mostrarOrigen) {
                Column {
                    SearchBarItem(
                        value = origen,
                        onValueChange = onOrigenChange,
                        placeholder = "Ubicació actual",
                        borderColor = Color(0xFFDDEEE5),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF74D3A2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            SearchBarItem(
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = "Destí",
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onFocus = onDestinoFocus
            )
        }
    }
}

@Composable
private fun RoutePriorityOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected) 5.dp else 0.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF5D6F8A) else Color(0xFFE7ECE8),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = iconBackground
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF23333A),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF77837D),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF3C577D),
                    unselectedColor = Color(0xFFC3CDD0)
                )
            )
        }
    }
}

@Composable
private fun RoutePlannerSheet(
    modifier: Modifier = Modifier,
    selectedPriority: RoutePriority,
    onPrioritySelected: (RoutePriority) -> Unit,
    distanceText: String,
    durationText: String,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prioritats de Ruta",
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
                            imageVector = Icons.Default.DirectionsWalk,
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
                        contentDescription = "Cerrar",
                        tint = Color(0xFF6C7772)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Seguretat",
                subtitle = "Zones il·luminades i concorregudes",
                selected = selectedPriority == RoutePriority.SAFETY,
                icon = Icons.Default.Security,
                iconBackground = Color(0xFF1F4A85),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.SAFETY) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Confort i Accessibilitat",
                subtitle = "Sense barreres i tranquil",
                selected = selectedPriority == RoutePriority.ACCESSIBILITY,
                icon = Icons.Default.Accessible,
                iconBackground = Color(0xFF7FD7AA),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.ACCESSIBILITY) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Emergència Tèrmica",
                subtitle = "Ombra i refugis climàtics",
                selected = selectedPriority == RoutePriority.HEAT,
                icon = Icons.Default.WbSunny,
                iconBackground = Color(0xFFFF7B42),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.HEAT) }
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    text = "•",
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC86A37),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Iniciar Ruta",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun MapLibreScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val mapView = rememberMapViewWithLifecycle()

    var destinoSeleccionado by remember { mutableStateOf<LatLng?>(null) }
    var textoOrigen by remember { mutableStateOf("") }
    var textoDestino by remember { mutableStateOf("") }
    var mapaListo by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var estiloSatelite by remember { mutableStateOf(false) }
    var mostrarOrigen by remember { mutableStateOf(false) }
    var prioridadSeleccionada by remember { mutableStateOf(RoutePriority.SAFETY) }
    var ultimaUbicacion by remember { mutableStateOf<Location?>(null) }
    var sheetHeightPx by remember { mutableStateOf(0f) }
    var sheetOffsetPx by remember { mutableStateOf(0f) }

    val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
    val collapsedSheetOffset = max(0f, sheetHeightPx - visibleSheetHeightPx)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = fine || coarse || hasLocationPermission(context)

        if (!locationGranted) {
            Toast.makeText(
                context,
                "Permiso de ubicación necesario para navegación.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val sheetDragState = rememberDraggableState { delta ->
        if (destinoSeleccionado != null) {
            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
        }
    }

    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(destinoSeleccionado) {
        if (destinoSeleccionado != null) {
            sheetOffsetPx = 0f
        }
    }

    DisposableEffect(mapaListo, locationGranted) {
        if (mapaListo && locationGranted) {
            activateLocationComponent(mapView)
            val listener = startAndroidLocationUpdates(context, mapView) {
                ultimaUbicacion = it
            }
            onDispose { stopAndroidLocationUpdates(context, listener) }
        } else {
            onDispose { }
        }
    }

    val (distanceText, durationText) = calculateDistanceAndDuration(
        currentLocation = ultimaUbicacion,
        destination = destinoSeleccionado
    )

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json") {
                            mapaListo = true
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = false

                            if (locationGranted) {
                                activateLocationComponent(this@apply)
                            }

                            map.addOnMapClickListener { point ->
                                map.clear()
                                map.addMarker(MarkerOptions().position(point))
                                destinoSeleccionado = point
                                textoDestino = String.format(
                                    Locale.US,
                                    "%.4f, %.4f",
                                    point.latitude,
                                    point.longitude
                                )
                                mostrarOrigen = true
                                true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        TopSearchPanel(
            origen = textoOrigen,
            onOrigenChange = { textoOrigen = it },
            destino = textoDestino,
            onDestinoChange = { textoDestino = it },
            mostrarOrigen = mostrarOrigen,
            onDestinoFocus = { mostrarOrigen = true }
        )

        AnimatedVisibility(
            visible = destinoSeleccionado != null,
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
                        sheetHeightPx = it.size.height.toFloat()
                        if (sheetOffsetPx > collapsedSheetOffset) {
                            sheetOffsetPx = collapsedSheetOffset
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
                                animate(
                                    initialValue = sheetOffsetPx,
                                    targetValue = target
                                ) { value, _ ->
                                    sheetOffsetPx = value
                                }
                            }
                        }
                    ),
                selectedPriority = prioridadSeleccionada,
                onPrioritySelected = { prioridadSeleccionada = it },
                distanceText = distanceText,
                durationText = durationText,
                onClose = {
                    destinoSeleccionado = null
                    textoDestino = ""
                    sheetOffsetPx = 0f
                    mapView.getMapAsync { it.clear() }
                },
                onStartRoute = {
                    val prioridad = when (prioridadSeleccionada) {
                        RoutePriority.SAFETY -> "Seguretat"
                        RoutePriority.ACCESSIBILITY -> "Confort i Accessibilitat"
                        RoutePriority.HEAT -> "Emergència Tèrmica"
                    }

                    Toast.makeText(
                        context,
                        "Iniciando ruta: $prioridad",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        AnimatedVisibility(
            visible = destinoSeleccionado == null,
            enter = slideInVertically(initialOffsetY = { it / 2 }),
            exit = slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = { estiloSatelite = !estiloSatelite },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Cambiar estilo del mapa"
                        )
                    },
                    text = {
                        Text(if (estiloSatelite) "Estándar" else "Satélite")
                    },
                    containerColor = if (estiloSatelite) Color(0xFF2F3B44) else Color.White,
                    contentColor = if (estiloSatelite) Color.White else Color(0xFF3D4A45)
                )

                Spacer(modifier = Modifier.height(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (locationGranted) {
                            activateLocationComponent(mapView)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49B97E)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "La meva ubicació")
                }
            }
        }
    }
}