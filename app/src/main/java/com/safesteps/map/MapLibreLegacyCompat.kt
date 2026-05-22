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

internal fun clearLegacyAnnotations(map: MapLibreMap) {
    map.clear()
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
