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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import java.util.Locale

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
    mapView: MapView
): LocationListener? {
    if (!hasLocationPermission(context)) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { location -> pushLocationToMap(mapView, location) }

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
                locationManager.getLastKnownLocation(provider)?.let { pushLocationToMap(mapView, it) }
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

@Composable
fun MapLibreScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()

    var destinoSeleccionado by remember { mutableStateOf<LatLng?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }
    var mapaListo by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }

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

    DisposableEffect(mapaListo, locationGranted) {
        if (mapaListo && locationGranted) {
            activateLocationComponent(mapView)
            val listener = startAndroidLocationUpdates(context, mapView)
            onDispose { stopAndroidLocationUpdates(context, listener) }
        } else {
            onDispose { }
        }
    }

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
                                true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(50)
        ) {
            TextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar en el mapa...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        AnimatedVisibility(
            visible = destinoSeleccionado != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            destinoSeleccionado?.let { latLng ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Ubicación seleccionada", fontWeight = FontWeight.Bold)
                                val latStr = String.format(Locale.US, "%.4f", latLng.latitude)
                                val lngStr = String.format(Locale.US, "%.4f", latLng.longitude)
                                Text(
                                    "Lat: $latStr, Lng: $lngStr",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            IconButton(
                                onClick = {
                                    destinoSeleccionado = null
                                    mapView.getMapAsync { it.clear() }
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Calculando ruta...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Calcular ruta")
                        }
                    }
                }
            }
        }

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
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = if (destinoSeleccionado != null) 200.dp else 16.dp
                )
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Mi ubicación")
        }
    }
}