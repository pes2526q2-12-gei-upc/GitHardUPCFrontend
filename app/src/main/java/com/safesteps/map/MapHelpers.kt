package com.safesteps.map

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory

fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun hasCoarseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun hasLocationPermission(context: Context): Boolean {
    return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
}

@Composable
fun rememberMapViewWithLifecycle(): MapView {
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

fun activateLocationComponent(mapView: MapView) {
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

fun pushLocationToMap(mapView: MapView, location: Location) {
    mapView.getMapAsync { map ->
        val locationComponent = map.locationComponent
        if (locationComponent.isLocationComponentActivated) {
            locationComponent.forceLocationUpdate(location)
        }
    }
}

fun startAndroidLocationUpdates(
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

fun stopAndroidLocationUpdates(context: Context, listener: LocationListener?) {
    if (listener == null) return
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    locationManager.removeUpdates(listener)
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