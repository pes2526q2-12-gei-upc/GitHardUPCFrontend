@file:Suppress("DEPRECATION")

package com.safesteps.map

import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal data class LegacyMarkerSelection(
    val position: LatLng,
    val title: String?,
    val snippet: String?
)

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
    icon: Icon? = null,
    snippet: String? = null
) {
    val markerOptions = MarkerOptions().position(position)

    if (title != null) {
        markerOptions.title(title)
    }

    if (icon != null) {
        markerOptions.icon(icon)
    }

    if (snippet != null) {
        markerOptions.snippet(snippet)
    }

    map.addMarker(markerOptions)
}

internal fun setLegacyMarkerClickListener(
    map: MapLibreMap,
    onMarkerClick: (LegacyMarkerSelection) -> Boolean
) {
    map.setOnMarkerClickListener { marker ->
        onMarkerClick(marker.toLegacyMarkerSelection())
    }
}

private fun Marker.toLegacyMarkerSelection(): LegacyMarkerSelection {
    return LegacyMarkerSelection(
        position = position,
        title = title,
        snippet = snippet
    )
}
