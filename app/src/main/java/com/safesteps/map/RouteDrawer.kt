@file:Suppress("DEPRECATION")

package com.safesteps.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import android.graphics.Canvas
import android.util.Log
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Brush
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.safesteps.data.Coordenada
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraUpdateFactory

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

// ─── Strategy interface ───────────────────────────────────────────────────────

private interface RouteDrawingStrategy {
    fun matches(routeColor: String?): Boolean
    fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>)
    fun primaryColor(): Int
    fun secondaryColor(): Int? = null
}
// ─── Strategies ───────────────────────────────────────────────────────────────

private class BlaugranaStrategy : RouteDrawingStrategy {
    override fun matches(routeColor: String?) = routeColor?.contains("BLAUGRANA") == true
    override fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>) {
        drawAlternatingSegments(style, puntsRuta, "#004D98".toColorInt(), "#A50044".toColorInt())
    }
    override fun primaryColor() = "#004D98".toColorInt()
    override fun secondaryColor() = "#A50044".toColorInt()
}

private class TaxiStrategy : RouteDrawingStrategy {
    override fun matches(routeColor: String?) = routeColor?.contains("TAXI") == true
    override fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>) {
        drawOverlappingLayers(style, puntsRuta, "#FFCC00".toColorInt(), "#1A1A1A".toColorInt(), 10f, 4f)
    }
    override fun primaryColor() = "#FFCC00".toColorInt()
    override fun secondaryColor() = "#1A1A1A".toColorInt()
}

private class NitStrategy : RouteDrawingStrategy {
    override fun matches(routeColor: String?) = routeColor?.contains("NIT") == true
    override fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>) {
        drawGradientSegments(style, puntsRuta, "#000000".toColorInt(), "#1A0030".toColorInt())
    }
    override fun primaryColor() = "#000000".toColorInt()
    override fun secondaryColor() = "#1A0030".toColorInt()
}

private class RgbFluidStrategy : RouteDrawingStrategy {
    override fun matches(routeColor: String?) = routeColor?.contains("RGB_FLUID") == true
    override fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>) {
        drawGradientSegments(style, puntsRuta, "#00FFFF".toColorInt(), "#FF00FF".toColorInt())
    }
    override fun primaryColor() = "#00FFFF".toColorInt()
    override fun secondaryColor() = "#FF00FF".toColorInt()
}

private const val DEFAULT_ROUTE_COLOR = "#1E88E5"

private class SolidColorStrategy(private val routeColor: String?) : RouteDrawingStrategy {
    override fun matches(routeColor: String?) = true
    override fun draw(style: org.maplibre.android.maps.Style, puntsRuta: List<LatLng>) {
        drawSolidLine(style, puntsRuta, primaryColor())
    }
    override fun primaryColor(): Int {
        if (routeColor == null) return DEFAULT_ROUTE_COLOR.toColorInt()
        if (routeColor.startsWith("#")) {
            return try { routeColor.toColorInt() } catch (e: Exception) { DEFAULT_ROUTE_COLOR.toColorInt() }
        }
        val regex = Regex("_R(\\d+)G(\\d+)B(\\d+)")
        val match = regex.find(routeColor)
        if (match != null) {
            val r = match.groupValues[1].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
            val g = match.groupValues[2].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
            val b = match.groupValues[3].toIntOrNull() ?: return DEFAULT_ROUTE_COLOR.toColorInt()
            return android.graphics.Color.rgb(r, g, b)
        }
        return DEFAULT_ROUTE_COLOR.toColorInt()
    }
}
// ─── Strategy registry ────────────────────────────────────────────────────────

private fun buildStrategies(routeColor: String?): List<RouteDrawingStrategy> = listOf(
    BlaugranaStrategy(),
    TaxiStrategy(),
    NitStrategy(),
    RgbFluidStrategy(),
    SolidColorStrategy(routeColor)
)

// ─── Entry point ──────────────────────────────────────────────────────────────

private fun drawRoutePolyline(
    map: org.maplibre.android.maps.MapLibreMap,
    puntsRuta: List<LatLng>,
    routeColor: String? = null
) {
    val style = map.style ?: return
    buildStrategies(routeColor)
        .first { it.matches(routeColor) }
        .draw(style, puntsRuta)
}

// ─── Drawing primitives ───────────────────────────────────────────────────────

private fun drawAlternatingSegments(
    style: org.maplibre.android.maps.Style,
    puntsRuta: List<LatLng>,
    color1: Int,
    color2: Int
) {
    puntsRuta.zipWithNext().forEachIndexed { index, (from, to) ->
        val color = if (index % 2 == 0) color1 else color2
        addSegmentLayer(style, index, from, to, color, 6f)
    }
}

private fun drawOverlappingLayers(
    style: org.maplibre.android.maps.Style,
    puntsRuta: List<LatLng>,
    colorBase: Int,
    colorTop: Int,
    widthBase: Float,
    widthTop: Float
) {
    val points = puntsRuta.toPoints()
    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, Feature.fromGeometry(LineString.fromLngLats(points))))
    style.addLayer(buildLineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID, colorBase, widthBase))
    style.addLayer(buildLineLayer(ROUTE_LAYER_ID_2, ROUTE_SOURCE_ID, colorTop, widthTop))
}

private fun drawGradientSegments(
    style: org.maplibre.android.maps.Style,
    puntsRuta: List<LatLng>,
    color1: Int,
    color2: Int
) {
    val segments = puntsRuta.zipWithNext()
    val total = segments.size
    segments.forEachIndexed { index, (from, to) ->
        val t = if (total <= 1) 0f else index.toFloat() / (total - 1)
        addSegmentLayer(style, index, from, to, interpolateColor(color1, color2, t), 6f)
    }
}

private fun drawSolidLine(
    style: org.maplibre.android.maps.Style,
    puntsRuta: List<LatLng>,
    color: Int
) {
    val points = puntsRuta.toPoints()
    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, Feature.fromGeometry(LineString.fromLngLats(points))))
    style.addLayer(buildLineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID, color, 6f))
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun addSegmentLayer(
    style: org.maplibre.android.maps.Style,
    index: Int,
    from: LatLng,
    to: LatLng,
    color: Int,
    width: Float
) {
    val sourceId = "route-source-$index"
    val layerId = "route-layer-$index"
    val points = listOf(
        Point.fromLngLat(from.longitude, from.latitude),
        Point.fromLngLat(to.longitude, to.latitude)
    )
    style.addSource(GeoJsonSource(sourceId, Feature.fromGeometry(LineString.fromLngLats(points))))
    style.addLayer(buildLineLayer(layerId, sourceId, color, width))
}

private fun buildLineLayer(
    layerId: String,
    sourceId: String,
    color: Int,
    width: Float
): LineLayer {
    return LineLayer(layerId, sourceId).withProperties(
        PropertyFactory.lineColor(color),
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineCap("round"),
        PropertyFactory.lineJoin("round")
    )
}

private fun List<LatLng>.toPoints(): List<Point> =
    map { Point.fromLngLat(it.longitude, it.latitude) }

private fun interpolateColor(color1: Int, color2: Int, t: Float): Int {
    val r = (android.graphics.Color.red(color1) * (1 - t) + android.graphics.Color.red(color2) * t).toInt()
    val g = (android.graphics.Color.green(color1) * (1 - t) + android.graphics.Color.green(color2) * t).toInt()
    val b = (android.graphics.Color.blue(color1) * (1 - t) + android.graphics.Color.blue(color2) * t).toInt()
    return android.graphics.Color.rgb(r, g, b)
}

fun resolveRouteBrushCompose(routeColor: String?): Brush {
    val strategy = buildStrategies(routeColor).first { it.matches(routeColor) }
    val primary = Color(strategy.primaryColor())
    val secondary = strategy.secondaryColor()?.let { Color(it) }
    return if (secondary != null) {
        Brush.horizontalGradient(colors = listOf(primary, secondary, primary))
    } else {
        Brush.verticalGradient(
            colors = listOf(
                primary,
                primary.copy(
                    red = (primary.red + 0.1f).coerceAtMost(1f),
                    blue = (primary.blue + 0.1f).coerceAtMost(1f)
                )
            )
        )
    }
}
fun resolveRouteColor(routeColor: String?): Int =
    buildStrategies(routeColor).first { it.matches(routeColor) }.primaryColor()

fun resolveRouteColorCompose(routeColor: String?): Color =
    Color(resolveRouteColor(routeColor))

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