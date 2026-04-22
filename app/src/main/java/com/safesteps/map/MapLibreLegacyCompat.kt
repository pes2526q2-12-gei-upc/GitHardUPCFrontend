@file:Suppress("DEPRECATION")

package com.safesteps.map

import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal fun clearLegacyAnnotations(map: MapLibreMap) {
    map.clear()
}

internal fun addLegacyMarker(
    map: MapLibreMap,
    position: LatLng,
    title: String? = null,
    icon: Icon? = null
) {
    val markerOptions = MarkerOptions().position(position)

    if (title != null) {
        markerOptions.title(title)
    }

    if (icon != null) {
        markerOptions.icon(icon)
    }

    map.addMarker(markerOptions)
}

internal fun setLegacyMarkerClickListener(
    map: MapLibreMap,
    onMarkerClick: (LatLng) -> Boolean
) {
    map.setOnMarkerClickListener { marker ->
        onMarkerClick(marker.position)
    }
}
