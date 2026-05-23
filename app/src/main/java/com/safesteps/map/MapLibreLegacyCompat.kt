@file:Suppress("DEPRECATION")

package com.safesteps.map

import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal const val ROUTE_SOURCE_ID = "route-source-solid"
internal const val ROUTE_LAYER_ID = "route-layer-solid"
internal const val ROUTE_LAYER_ID_2 = "route-layer-solid-2"

internal fun clearLegacyAnnotations(map: MapLibreMap) {
    map.clear()
    val style = map.style ?: return

    // Limpiar color simple y overlapping
    style.removeLayer(ROUTE_LAYER_ID)
    style.removeLayer(ROUTE_LAYER_ID_2)
    style.removeSource(ROUTE_SOURCE_ID)

    // Limpiar segmentos de degradado y alternos
    for (i in 0..999) {
        val layerId = "route-layer-$i"
        val sourceId = "route-source-$i"
        if (style.getLayer(layerId) != null) {
            style.removeLayer(layerId)
            style.removeSource(sourceId)
        } else {
            break
        }
    }
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
