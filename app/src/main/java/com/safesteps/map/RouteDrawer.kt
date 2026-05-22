@file:Suppress("DEPRECATION")

package com.safesteps.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.safesteps.data.Coordenada
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

fun drawRoute(
    mapView: MapView,
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?,
    context: Context,
    originTitle: String,
    destinationTitle: String,
    animateCamera: Boolean = true,
    routeColor: String? = null
) {
    if (coordenades.isEmpty()) return

    mapView.getMapAsync { map ->
        if (map.style?.isFullyLoaded != true) return@getMapAsync

        disableLocationCamera(map)
        clearLegacyAnnotations(map)
        val puntsRuta = buildRoutePoints(coordenades, origen, desti)
        drawRoutePolyline(map, puntsRuta, routeColor)
        addRouteMarkers(map, origen, desti, context, originTitle, destinationTitle)
        if (animateCamera) {
            animateCameraToRoute(map, puntsRuta)
        }
    }
}

private fun disableLocationCamera(map: org.maplibre.android.maps.MapLibreMap) {
    val locationComponent = map.locationComponent
    if (locationComponent.isLocationComponentActivated) {
        locationComponent.cameraMode = CameraMode.NONE
    }
}

private fun buildRoutePoints(
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?
): List<LatLng> {
    val puntsRuta = coordenades.map { LatLng(it.lat, it.lon) }.toMutableList()
    addOriginIfNeeded(puntsRuta, origen)
    addDestinationIfNeeded(puntsRuta, desti)
    return puntsRuta
}

private fun addOriginIfNeeded(puntsRuta: MutableList<LatLng>, origen: LatLng?) {
    if (origen == null || puntsRuta.isEmpty()) return
    if (!hasSameCoordinates(origen, puntsRuta.first())) {
        puntsRuta.add(0, origen)
    }
}

private fun addDestinationIfNeeded(puntsRuta: MutableList<LatLng>, desti: LatLng?) {
    if (desti == null || puntsRuta.isEmpty()) return
    if (!hasSameCoordinates(desti, puntsRuta.last())) {
        puntsRuta.add(desti)
    }
}

private fun hasSameCoordinates(first: LatLng, second: LatLng): Boolean {
    return first.latitude == second.latitude && first.longitude == second.longitude
}

private fun drawRoutePolyline(
    map: org.maplibre.android.maps.MapLibreMap,
    puntsRuta: List<LatLng>,
    routeColor: String? = null
) {
    val patternColors = resolvePatternColors(routeColor)

    if (patternColors != null) {
        val (color1, color2) = patternColors
        // Línea base más gruesa con color1
        map.addPolyline(
            PolylineOptions()
                .addAll(puntsRuta)
                .color(color1)
                .width(10f)
        )
        // Línea fina encima con color2
        map.addPolyline(
            PolylineOptions()
                .addAll(puntsRuta)
                .color(color2)
                .width(4f)
        )
    } else {
        map.addPolyline(
            PolylineOptions()
                .addAll(puntsRuta)
                .color(resolveRouteColor(routeColor))
                .width(6f)
        )
    }
}

// Devuelve par de colores si es patrón de dos colores, null si es color simple
private fun resolvePatternColors(routeColor: String?): Pair<Int, Int>? {
    if (routeColor == null) return null
    return when {
        routeColor.contains("BLAUGRANA") ->
            Pair("#004D98".toColorInt(), "#A50044".toColorInt())
        routeColor.contains("TAXI") ->
            Pair("#FFCC00".toColorInt(), "#1A1A1A".toColorInt())
        routeColor.contains("NIT") ->
            Pair("#0A0A1A".toColorInt(), "#2E2E5E".toColorInt())
        routeColor.contains("RGB_FLUID") ->
            Pair("#00FFFF".toColorInt(), "#FF00FF".toColorInt())
        else -> null
    }
}

private const val DEFAULT_ROUTE_COLOR = "#1E88E5"

private fun resolveRouteColor(routeColor: String?): Int {
    if (routeColor == null) return DEFAULT_ROUTE_COLOR.toColorInt()
    // Si es un hex normal, lo parseamos directamente
    if (routeColor.startsWith("#")) {
        return try { routeColor.toColorInt() } catch (e: Exception) { DEFAULT_ROUTE_COLOR.toColorInt() }
    }
    // Si es un ID de premio de color, extraemos el color del ID
    val regex = Regex("_R(\\d+)G(\\d+)B(\\d+)")
    val match = regex.find(routeColor)
    if (match != null) {
        val r = match.groupValues[1].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
        val g = match.groupValues[2].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
        val b = match.groupValues[3].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
        return android.graphics.Color.rgb(r, g, b)
    }
    // Patrones especiales — usamos el primer color representativo
    return when {
        routeColor.contains("BLAUGRANA") -> "#004D98".toColorInt()
        routeColor.contains("TAXI")      -> "#FFCC00".toColorInt()
        routeColor.contains("NIT")       -> "#1B1B3A".toColorInt()
        routeColor.contains("RGB_FLUID") -> "#00FFFF".toColorInt()
        else -> DEFAULT_ROUTE_COLOR.toColorInt()
    }
}

private fun addRouteMarkers(
    map: org.maplibre.android.maps.MapLibreMap,
    origen: LatLng?,
    desti: LatLng?,
    context: Context,
    originTitle: String,
    destinationTitle: String
) {
    origen?.let {
        addLegacyMarker(
            map = map,
            position = it,
            title = originTitle,
            icon = crearIconaGrisa(context)
        )
    }

    desti?.let {
        addLegacyMarker(
            map = map,
            position = it,
            title = destinationTitle
        )
    }
}

private fun animateCameraToRoute(map: org.maplibre.android.maps.MapLibreMap, puntsRuta: List<LatLng>) {
    if (puntsRuta.size <= 1) return

    val boundsBuilder = LatLngBounds.Builder()
    puntsRuta.forEach(boundsBuilder::include)
    val bounds = boundsBuilder.build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 350), 1000)
}

fun crearIconaPoi(context: Context, tipus: String): Icon {
    val normalizedType = tipus.trim().uppercase()
    val (emoji, bgColor) = when (normalizedType) {
        "FONT" -> "\uD83D\uDCA7" to "#3DA5F4".toColorInt()
        "COMISSARIA" -> "\uD83D\uDC6E" to "#355C7D".toColorInt()
        else -> "\uD83D\uDCCD" to "#C86A37".toColorInt()
    }

    val size = 96
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.7f, shadowPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, circlePaint)

    val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(emoji, size / 2f, y, textPaint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

fun drawRouteOnMap(
    map: MapLibreMap,
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?,
    context: Context,
    originTitle: String,
    destinationTitle: String,
    animateCamera: Boolean = true,
    routeColor: String? = null
) {
    if (coordenades.isEmpty()) return
    if (map.style?.isFullyLoaded != true) return

    disableLocationCamera(map)
    val puntsRuta = buildRoutePoints(coordenades, origen, desti)
    drawRoutePolyline(map, puntsRuta, routeColor)
    addRouteMarkers(map, origen, desti, context, originTitle, destinationTitle)
    if (animateCamera) {
        animateCameraToRoute(map, puntsRuta)
    }
}