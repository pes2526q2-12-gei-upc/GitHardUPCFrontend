package com.safesteps.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.safesteps.data.Coordenada
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
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

        val puntsRuta = coordenades.map { LatLng(it.lat, it.lon) }.toMutableList()

        origen?.let {
            if (puntsRuta.isNotEmpty() && (it.latitude != puntsRuta.first().latitude || it.longitude != puntsRuta.first().longitude)) {
                puntsRuta.add(0, it)
            }
        }
        desti?.let {
            if (puntsRuta.isNotEmpty() && (it.latitude != puntsRuta.last().latitude || it.longitude != puntsRuta.last().longitude)) {
                puntsRuta.add(it)
            }
        }

        map.addPolyline(
            PolylineOptions()
                .addAll(puntsRuta)
                .color(Color.parseColor("#1E88E5"))
                .width(6f)
        )

        origen?.let {
            map.addMarker(MarkerOptions()
                .position(it)
                .title("Origen")
                .icon(crearIconaGrisa(context)))
        }

        desti?.let {
            map.addMarker(MarkerOptions()
                .position(it)
                .title("Destí"))
        }

        if (puntsRuta.size > 1) {
            val boundsBuilder = LatLngBounds.Builder()
            puntsRuta.forEach { boundsBuilder.include(it) }

            val bounds = boundsBuilder.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 350), 1000)
        }
    }
}

fun crearIconaPoi(context: Context, tipus: String): Icon {
    val normalizedType = tipus.trim().uppercase()
    val (emoji, bgColor) = when (normalizedType) {
        "FONT" -> "💧" to Color.parseColor("#3DA5F4")
        "COMISSARIA" -> "👮" to Color.parseColor("#355C7D")
        else -> "📍" to Color.parseColor("#C86A37")
    }

    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.7f, shadowPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, circlePaint)

    val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(emoji, size / 2f, y, textPaint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}
