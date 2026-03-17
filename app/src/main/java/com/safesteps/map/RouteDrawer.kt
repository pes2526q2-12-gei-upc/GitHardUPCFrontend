package com.safesteps.map

import android.graphics.Color
import com.safesteps.data.Coordenada
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

fun drawRoute(
    mapView: MapView,
    coordenades: List<Coordenada>
) {
    if (coordenades.isEmpty()) return

    mapView.getMapAsync { map ->
        if (map.style?.isFullyLoaded != true) return@getMapAsync

        map.clear()

        val punts = coordenades.map { LatLng(it.lat, it.lon) }

        map.addPolyline(
            PolylineOptions()
                .addAll(punts)
                .color(Color.parseColor("#1E88E5"))
                .width(6f)
        )
    }
}