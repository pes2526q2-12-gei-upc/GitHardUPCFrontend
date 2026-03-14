package com.safesteps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Canvas
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import kotlinx.coroutines.delay
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraUpdateFactory

private enum class RoutePriority {
    SAFETY,
    ACCESSIBILITY,
    HEAT
}

private enum class textField {
    NONE,
    ORIGIN,
    DESTINY
}

fun crearIconaGrisa(context: android.content.Context): Icon {
    // Creem un llenç buit de 40x40 píxels
    val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Configurem el pinzell de color gris
    val paint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        isAntiAlias = true
    }

    // Dibuixem una rodona al mig
    canvas.drawCircle(20f, 20f, 20f, paint)

    // Ho convertim a una icona compatible amb MapLibre
    return IconFactory.getInstance(context).fromBitmap(bitmap)
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
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return@getMapAsync

        val style = map.style ?: return@getMapAsync
        val locationComponent = map.locationComponent

        if (!locationComponent.isLocationComponentActivated) {
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor(AndroidColor.parseColor("#1E88E5"))
                .backgroundTintColor(AndroidColor.WHITE)
                .bearingTintColor(AndroidColor.parseColor("#1E88E5"))
                .accuracyColor(AndroidColor.parseColor("#5533B5E5"))
                .pulseEnabled(false)
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
            locationComponent.renderMode = RenderMode.NORMAL
            // LA CLAU ESTÀ AQUÍ: No forcem el tracking perquè no es baralli amb la nostra animació manual
            locationComponent.cameraMode = CameraMode.NONE
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
    textColor: Color = Color(0xFF3D4A45),
    placeholderColor: Color = Color(0xFF9AA7A0),
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
                        color = placeholderColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor
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
private fun DropdownSuggeriments(
    adrecesSuggerides: List<Feature>,
    onAdrecaSeleccionada: (Feature) -> Unit
) {
    AnimatedVisibility(visible = adrecesSuggerides.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE4ECE8), RoundedCornerShape(16.dp))
        ) {
            adrecesSuggerides.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdrecaSeleccionada(feature) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF9AA7A0),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feature.properties.getAddress(),
                        color = Color(0xFF3D4A45),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (feature != adrecesSuggerides.last()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFF4F6F5))
                    )
                }
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
    onOrigenFocus: () -> Unit,
    onDestinoFocus: () -> Unit,
    adrecesSuggerides: List<Feature>,
    onAdrecaSeleccionada: (Feature) -> Unit,
    campActiu: textField
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Capçalera Menu i Perfil
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFFF4F6F5)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Menu, null, tint = Color(0xFF66716C), modifier = Modifier.size(18.dp)) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    Text("SafeSteps", color = Color(0xFF33413B), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = Color(0xFF6DD29A)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ZONA ORIGEN
            AnimatedVisibility(visible = mostrarOrigen) {
                Column {
                    val esUbicacioActual = origen.isBlank() && campActiu != textField.ORIGIN
                    SearchBarItem(
                        value = origen,
                        onValueChange = onOrigenChange,
                        placeholder = "La meva ubicació",
                        borderColor = if (esUbicacioActual) Color(0xFFBBDEFB) else Color(0xFFDDEEE5),
                        textColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF3D4A45),
                        placeholderColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF9AA7A0),
                        leadingIcon = {
                            Icon(
                                if (esUbicacioActual) Icons.Default.MyLocation else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF74D3A2),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onFocus = onOrigenFocus
                    )

                    // Llista desplegable de l'Origen (Només surt si estem tocant l'origen)
                    if (campActiu == textField.ORIGIN) {
                        DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ZONA DESTÍ
            SearchBarItem(
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = "Destí",
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp)) },
                onFocus = onDestinoFocus
            )

            // Llista desplegable del Destí (Només surt si estem tocant el destí)
            if (campActiu == textField.DESTINY) {
                DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
            }
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

suspend fun getTextoDestino(point: LatLng): String {
    return try {
        val resposta = PhotonApi.service.reverseGeocode(
            lat = point.latitude,
            lon = point.longitude
        )
        val adreca = resposta.features.firstOrNull()?.properties?.getAddress()

        // Si no troba adreca o ens retorna variables rares de sistema, posem un text genèric net
        if (adreca.isNullOrBlank() || adreca.contains("LatLng") || adreca.contains("Location")) {
            "${point.longitude}, ${point.latitude}"
        } else {
            adreca
        }
    } catch (e: Exception) {
        "Ubicació seleccionada al mapa"
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
    var adrecesSuggerides by remember { mutableStateOf<List<Feature>>(emptyList()) }
    var campActiu by remember { mutableStateOf(textField.NONE) }
    var origenSeleccionado by remember { mutableStateOf<LatLng?>(null) }
    var isTyping by remember { mutableStateOf(false) }
    var firstLocationZoomDone by remember { mutableStateOf(false) }

    // 1. Cercador automàtic
    LaunchedEffect(textoOrigen, textoDestino, campActiu, isTyping) {
        val textCerca = if (campActiu == textField.ORIGIN) textoOrigen else textoDestino
        if (isTyping && campActiu != textField.NONE && textCerca.length >= 3) {
            delay(300)
            try {
                val resposta = PhotonApi.service.findAddress(query = textCerca)
                adrecesSuggerides = resposta.features.distinctBy { it.properties.getAddress() }
            } catch (e: Exception) {
                adrecesSuggerides = emptyList()
            }
        } else {
            adrecesSuggerides = emptyList()
        }
    }

    LaunchedEffect(estiloSatelite) {
        mapView.getMapAsync { map ->
            val styleUrl = if (estiloSatelite) "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json" else "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"

            map.setStyle(styleUrl) {
                mapaListo = true
                if (locationGranted) {
                    activateLocationComponent(mapView)
                }
                // Si canviem d'estil, ens assegurem de tornar a pintar els marcadors que hi havia
                map.clear()
                origenSeleccionado?.let { ori -> map.addMarker(MarkerOptions().position(ori).title("Origen").icon(crearIconaGrisa(context))) }
                destinoSeleccionado?.let { dest -> map.addMarker(MarkerOptions().position(dest).title("Destí")) }
            }
        }
    }

    // 2. PINTOR DE XINXETES (Actua ràpidament cada cop que toques el mapa)
    LaunchedEffect(origenSeleccionado, destinoSeleccionado) {
        mapView.getMapAsync { map ->
            // Només pintem si el fons del mapa ja està completament carregat
            if (map.style?.isFullyLoaded == true) {
                map.clear()
                origenSeleccionado?.let { ori -> map.addMarker(MarkerOptions().position(ori).title("Origen").icon(crearIconaGrisa(context))) }
                destinoSeleccionado?.let { dest -> map.addMarker(MarkerOptions().position(dest).title("Destí")) }
            }
        }
    }

    // 3. Escoltar GPS sempre de fons (Independent del mapa)
    DisposableEffect(locationGranted) {
        var listener: LocationListener? = null
        if (locationGranted) {
            listener = startAndroidLocationUpdates(context, mapView) { loc ->
                ultimaUbicacion = loc
            }
        }
        onDispose { stopAndroidLocationUpdates(context, listener) }
    }

    // 4. Zoom inicial de benvinguda (S'espera a tenir ubicació i mapa)
    LaunchedEffect(ultimaUbicacion, mapaListo) {
        if (ultimaUbicacion != null && mapaListo && !firstLocationZoomDone) {
            firstLocationZoomDone = true
            delay(500) // <-- LA CLAU: Donem mig segon perquè el mapa tingui mida a la pantalla
            mapView.getMapAsync { map ->
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(ultimaUbicacion!!.latitude, ultimaUbicacion!!.longitude),
                        15.0
                    ),
                    1500
                )
            }
        }
    }

    val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
    val collapsedSheetOffset = max(0f, sheetHeightPx - visibleSheetHeightPx)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = fine || coarse || hasLocationPermission(context)
        if (!locationGranted) Toast.makeText(context, "Permís d'ubicació necessari.", Toast.LENGTH_SHORT).show()
    }

    val sheetDragState = rememberDraggableState { delta ->
        if (destinoSeleccionado != null) {
            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
        }
    }

    LaunchedEffect(Unit) {
        if (!locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(destinoSeleccionado) {
        if (destinoSeleccionado != null) sheetOffsetPx = 0f
    }

    val origenPerCàlcul = origenSeleccionado?.let { Location("manual").apply { latitude = it.latitude; longitude = it.longitude } } ?: ultimaUbicacion
    val (distanceText, durationText) = calculateDistanceAndDuration(currentLocation = origenPerCàlcul, destination = destinoSeleccionado)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        // Aquest detector de clics ara és independent i no fallarà mai
                        map.addOnMapClickListener { point ->
                            destinoSeleccionado = point
                            mostrarOrigen = true
                            campActiu = textField.NONE
                            isTyping = false

                            // Animem la càmera
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.0), 1000)

                            // Actualitzem el text amb seguretat
                            textoDestino = "Buscant adreça..."
                            coroutineScope.launch {
                                try {
                                    val adrecaResultat = getTextoDestino(point)
                                    textoDestino = if (adrecaResultat.contains("LatLng") || adrecaResultat.contains("Location")) {
                                        "Ubicació seleccionada al mapa"
                                    } else {
                                        adrecaResultat
                                    }
                                } catch (e: Exception) {
                                    textoDestino = "Ubicació seleccionada al mapa"
                                }
                            }
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        TopSearchPanel(
            origen = textoOrigen, onOrigenChange = { textoOrigen = it; campActiu = textField.ORIGIN; isTyping = true },
            destino = textoDestino, onDestinoChange = { textoDestino = it; campActiu = textField.DESTINY; isTyping = true },
            mostrarOrigen = mostrarOrigen,
            onOrigenFocus = { campActiu = textField.ORIGIN; isTyping = false },
            onDestinoFocus = { mostrarOrigen = true; campActiu = textField.DESTINY; isTyping = false },
            adrecesSuggerides = adrecesSuggerides,
            onAdrecaSeleccionada = { feature ->
                isTyping = false
                val puntSeleccionat = LatLng(feature.geometry.latitud, feature.geometry.longitud)
                val textAdreca = feature.properties.getAddress()

                if (campActiu == textField.ORIGIN) {
                    textoOrigen = textAdreca
                    origenSeleccionado = puntSeleccionat
                } else {
                    textoDestino = textAdreca
                    destinoSeleccionado = puntSeleccionat
                }

                mapView.getMapAsync { map -> map.animateCamera(CameraUpdateFactory.newLatLngZoom(puntSeleccionat, 15.0), 1500) }
                campActiu = textField.NONE
                adrecesSuggerides = emptyList()
            },
            campActiu = campActiu
        )

        AnimatedVisibility(
            visible = destinoSeleccionado != null,
            enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)
        ) {
            RoutePlannerSheet(
                modifier = Modifier
                    .offset(y = with(density) { sheetOffsetPx.toDp() })
                    .onGloballyPositioned {
                        sheetHeightPx = it.size.height.toFloat()
                        if (sheetOffsetPx > collapsedSheetOffset) sheetOffsetPx = collapsedSheetOffset
                    }
                    .draggable(
                        orientation = Orientation.Vertical, state = sheetDragState,
                        onDragStopped = {
                            val target = if (sheetOffsetPx > collapsedSheetOffset / 2f) collapsedSheetOffset else 0f
                            coroutineScope.launch { animate(initialValue = sheetOffsetPx, targetValue = target) { value, _ -> sheetOffsetPx = value } }
                        }
                    ),
                selectedPriority = prioridadSeleccionada,
                onPrioritySelected = { prioridadSeleccionada = it },
                distanceText = distanceText, durationText = durationText,
                onClose = {
                    destinoSeleccionado = null
                    textoDestino = ""
                    sheetOffsetPx = 0f
                    mapView.getMapAsync { map ->
                        map.clear()
                        origenSeleccionado?.let { ori -> map.addMarker(MarkerOptions().position(ori).title("Origen").icon(crearIconaGrisa(context))) }
                    }
                },
                onStartRoute = { Toast.makeText(context, "Calculant ruta...", Toast.LENGTH_SHORT).show() }
            )
        }

        AnimatedVisibility(
            visible = destinoSeleccionado == null,
            enter = slideInVertically(initialOffsetY = { it / 2 }), exit = slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = { estiloSatelite = !estiloSatelite },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Canviar estil") },
                    text = { Text(if (estiloSatelite) "Estàndard" else "Satèl·lit") },
                    containerColor = if (estiloSatelite) Color(0xFF2F3B44) else Color.White,
                    contentColor = if (estiloSatelite) Color.White else Color(0xFF3D4A45)
                )

                Spacer(modifier = Modifier.height(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (locationGranted) {
                            origenSeleccionado = null
                            textoOrigen = ""
                            campActiu = textField.NONE
                            isTyping = false

                            activateLocationComponent(mapView)

                            if (ultimaUbicacion != null) {
                                mapView.getMapAsync { map ->
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        LatLng(ultimaUbicacion!!.latitude, ultimaUbicacion!!.longitude), 15.0), 1000)
                                }
                            } else {
                                Toast.makeText(context, "Buscant senyal GPS...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49B97E)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "La meva ubicació")
                }
            }
        }
    }
}