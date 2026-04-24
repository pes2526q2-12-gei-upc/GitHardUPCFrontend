@file:Suppress("DEPRECATION")

package com.safesteps.map

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView

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

@SuppressLint("MissingPermission")
fun getBestLastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fineGranted = hasFineLocationPermission(context)
    val coarseGranted = hasCoarseLocationPermission(context)

    val providers = buildList {
        if (fineGranted) add(LocationManager.GPS_PROVIDER)
        if (fineGranted || coarseGranted) add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }.distinct()

    return providers
        .mapNotNull { provider ->
            try {
                if (
                    provider != LocationManager.PASSIVE_PROVIDER &&
                    !locationManager.isProviderEnabled(provider)
                ) {
                    null
                } else {
                    locationManager.getLastKnownLocation(provider)
                }
            } catch (_: SecurityException) {
                null
            }
        }
        .maxByOrNull { it.time }
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

fun activateLocationComponent(
    mapView: MapView,
    initialLocation: Location? = null
) {
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

        if (!fineGranted && !coarseGranted) return@getMapAsync

        val style = map.style ?: return@getMapAsync
        val locationComponent = map.locationComponent

        if (!locationComponent.isLocationComponentActivated) {
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor("#1E88E5".toColorInt())
                .backgroundTintColor(AndroidColor.WHITE)
                .bearingTintColor("#1E88E5".toColorInt())
                .accuracyColor("#5533B5E5".toColorInt())
                .pulseEnabled(false)
                .pulseColor("#8833B5E5".toColorInt())
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
            locationComponent.cameraMode = CameraMode.NONE
            resolveInitialLocation(context, initialLocation)?.let(locationComponent::forceLocationUpdate)
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

fun centerMapOnLocation(
    mapView: MapView,
    location: Location,
    zoom: Double = 15.0,
    durationMs: Int = 1000
) {
    mapView.getMapAsync { map ->
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                zoom
            ),
            durationMs
        )
    }
}

@SuppressLint("MissingPermission")
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
        if (fineGranted || coarseGranted) add(LocationManager.PASSIVE_PROVIDER)
    }

    getBestLastKnownLocation(context)?.let { location ->
        pushLocationToMap(mapView, location)
        onLocationUpdated(location)
    }

    for (provider in providers) {
        try {
            val providerAvailable = provider == LocationManager.PASSIVE_PROVIDER || locationManager.isProviderEnabled(provider)
            if (providerAvailable) {
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

@SuppressLint("MissingPermission")
fun stopAndroidLocationUpdates(context: Context, listener: LocationListener?) {
    if (listener == null) return
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    locationManager.removeUpdates(listener)
}

fun crearIconaGrisa(context: Context): Icon {
    val bitmap = createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        color = "#9EAEB5".toColorInt()
        isAntiAlias = true
    }

    canvas.drawCircle(20f, 20f, 20f, paint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun resolveInitialLocation(
    context: Context,
    preferredLocation: Location?
): Location? {
    return preferredLocation ?: getBestLastKnownLocation(context)
}
