@file:Suppress("DEPRECATION")

package com.safesteps.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.maplibre.android.maps.MapView

fun drawRoute(
    mapView: MapView,
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?,
    context: Context,
    originTitle: String,
    destinationTitle: String,
    clearAnnotations: Boolean = true,
    disableLocationCameraBeforeDraw: Boolean = true,
    fitCameraToRoute: Boolean = true
) {
    if (coordenades.isEmpty()) return

    mapView.getMapAsync { map ->
        if (map.style?.isFullyLoaded != true) return@getMapAsync
        drawRouteOnMap(
            map = map,
            coordenades = coordenades,
            origen = origen,
            desti = desti,
            context = context,
            originTitle = originTitle,
            destinationTitle = destinationTitle,
            clearAnnotations = clearAnnotations,
            disableLocationCameraBeforeDraw = disableLocationCameraBeforeDraw,
            fitCameraToRoute = fitCameraToRoute
        )
    }
}

internal fun drawRouteOnMap(
    map: org.maplibre.android.maps.MapLibreMap,
    coordenades: List<Coordenada>,
    origen: LatLng?,
    desti: LatLng?,
    context: Context,
    originTitle: String,
    destinationTitle: String,
    clearAnnotations: Boolean = true,
    disableLocationCameraBeforeDraw: Boolean = true,
    fitCameraToRoute: Boolean = true
) {
    if (coordenades.isEmpty()) {
        return
    }

    if (disableLocationCameraBeforeDraw) {
        disableLocationCamera(map)
    }
    if (clearAnnotations) {
        clearLegacyAnnotations(map)
    }
    val puntsRuta = buildRoutePoints(coordenades, origen, desti)
    drawRoutePolyline(map, puntsRuta)
    addRouteMarkers(map, origen, desti, context, originTitle, destinationTitle)
    if (fitCameraToRoute) {
        animateCameraToRoute(map, puntsRuta)
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

private fun drawRoutePolyline(map: org.maplibre.android.maps.MapLibreMap, puntsRuta: List<LatLng>) {
    map.addPolyline(
        PolylineOptions()
            .addAll(puntsRuta)
            .color("#1E88E5".toColorInt())
            .width(6f)
    )
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
}
