package com.safesteps.map

import android.content.Context
import android.graphics.Color
import com.safesteps.data.Coordenada
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.location.modes.CameraMode

fun drawRoute(
    mapView: MapView,
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?,
    context: Context
) {
    if (coordenades.isEmpty()) return

    mapView.getMapAsync { map ->
        if (map.style?.isFullyLoaded != true) return@getMapAsync

        val locationComponent = map.locationComponent
        if (locationComponent.isLocationComponentActivated) {
            locationComponent.cameraMode = CameraMode.NONE
        }

        map.clear()

        val punts = coordenades.map { LatLng(it.lat, it.lon) }

        map.addPolyline(
            PolylineOptions()
                .addAll(punts)
                .color(Color.parseColor("#1E88E5"))
                .width(6f)
        )
        origen?.let {
            map.addMarker(MarkerOptions()
                .position(it)
                .title("Origen")
                .icon(crearIconaGrisa(context)))
        }
        desti?.let { map.addMarker(MarkerOptions().position(it).title("Destí")) }

        if (punts.size > 1) {
            val boundsBuilder = LatLngBounds.Builder()
            punts.forEach { boundsBuilder.include(it) }
            origen?.let { boundsBuilder.include(it) }
            desti?.let { boundsBuilder.include(it) }

            val bounds = boundsBuilder.build()

            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 350), 1000)
        }
    }
}