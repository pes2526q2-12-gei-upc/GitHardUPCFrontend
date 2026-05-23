@file:Suppress("DEPRECATION")

package com.safesteps.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import android.graphics.Canvas
import android.util.Log
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    val white = android.graphics.Color.WHITE
    val cameraBlack = "#101010".toColorInt()
    val benchGray = "#8A9096".toColorInt()
    val (emoji, bgColor, foregroundColor) = when (normalizedType) {
        "FONT" -> Triple("\uD83D\uDCA7", "#3DA5F4".toColorInt(), white)
        "COMISSARIA" -> Triple("\uD83D\uDC6E", "#355C7D".toColorInt(), white)
        "BANC" -> Triple("", white, benchGray)
        "CAMERA" -> Triple("", white, cameraBlack)
        "ESCALA_MECANICA" -> Triple("", "#5C6F7B".toColorInt(), white)
        "REFUGI_CLIMATIC" -> Triple("", "#F28C28".toColorInt(), white)
        "ESDEVENIMENT" -> Triple("", "#B8522A".toColorInt(), white)
        else -> Triple("\uD83D\uDCCD", "#C86A37".toColorInt(), white)
    }

    val size = 96
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foregroundColor
        textAlign = Paint.Align.CENTER
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(foregroundColor, 70)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.7f, shadowPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, circlePaint)
    if (bgColor == white) {
        canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, borderPaint)
    }

    when (normalizedType) {
        "BANC" -> drawStreetBenchSymbol(canvas, size, foregroundColor)
        "ESCALA_MECANICA" -> drawEscalatorSymbol(canvas, size)
        "REFUGI_CLIMATIC" -> drawThermometerSymbol(canvas, size)
        "CAMERA" -> drawSecurityCameraSymbol(canvas, size, bgColor, foregroundColor)
        "ESDEVENIMENT" -> drawEventCalendarSymbol(canvas, size, foregroundColor)
        else -> {
            val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(emoji, size / 2f, y, textPaint)
        }
    }

    return IconFactory.getInstance(context).fromBitmap(bitmap)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun drawEventCalendarSymbol(
    canvas: Canvas,
    size: Int,
    symbolColor: Int
) {
    val cardRect = RectF(
        size * 0.25f,
        size * 0.27f,
        size * 0.75f,
        size * 0.72f
    )
    val corner = size * 0.07f

    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.STROKE
        strokeWidth = size * 0.055f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.FILL
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.FILL
    }

    canvas.drawRoundRect(cardRect, corner, corner, framePaint)

    val topBarHeight = size * 0.11f
    canvas.drawRoundRect(
        RectF(
            cardRect.left,
            cardRect.top,
            cardRect.right,
            cardRect.top + topBarHeight
        ),
        corner,
        corner,
        barPaint
    )

    val ringStroke = size * 0.045f
    val leftRingX = cardRect.left + size * 0.11f
    val rightRingX = cardRect.right - size * 0.11f
    val ringTop = cardRect.top - size * 0.06f
    val ringBottom = cardRect.top + size * 0.02f
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(leftRingX, ringTop, leftRingX, ringBottom, ringPaint)
    canvas.drawLine(rightRingX, ringTop, rightRingX, ringBottom, ringPaint)

    val dotRadius = size * 0.038f
    val startX = cardRect.left + size * 0.12f
    val startY = cardRect.top + size * 0.22f
    val step = size * 0.12f
    repeat(2) { row ->
        repeat(2) { column ->
            canvas.drawCircle(
                startX + column * step,
                startY + row * step,
                dotRadius,
                dotPaint
            )
        }
    }
}

private fun drawSecurityCameraSymbol(
    canvas: Canvas,
    size: Int,
    backgroundColor: Int,
    symbolColor: Int
) {
    val cameraStrokeWidth = size * 0.05f

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.FILL
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.STROKE
        strokeWidth = cameraStrokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(symbolColor, 130)
        style = Paint.Style.STROKE
        strokeWidth = size * 0.028f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val lensCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    val lensHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255)
        style = Paint.Style.FILL
    }

    val bodyPath = Path().apply {
        moveTo(size * 0.27f, size * 0.37f)
        lineTo(size * 0.66f, size * 0.31f)
        quadTo(size * 0.78f, size * 0.29f, size * 0.82f, size * 0.39f)
        lineTo(size * 0.84f, size * 0.50f)
        quadTo(size * 0.85f, size * 0.58f, size * 0.74f, size * 0.61f)
        lineTo(size * 0.37f, size * 0.66f)
        quadTo(size * 0.24f, size * 0.68f, size * 0.21f, size * 0.56f)
        lineTo(size * 0.18f, size * 0.46f)
        quadTo(size * 0.16f, size * 0.39f, size * 0.27f, size * 0.37f)
        close()
    }
    canvas.drawPath(bodyPath, bodyPaint)
    canvas.drawLine(size * 0.34f, size * 0.42f, size * 0.56f, size * 0.39f, accentPaint)

    val lensCenterX = size * 0.31f
    val lensCenterY = size * 0.52f
    canvas.drawCircle(lensCenterX, lensCenterY, size * 0.11f, detailPaint)
    canvas.drawCircle(lensCenterX, lensCenterY, size * 0.07f, lensCutoutPaint)
    canvas.drawCircle(lensCenterX, lensCenterY, size * 0.026f, bodyPaint)
    canvas.drawCircle(size * 0.27f, size * 0.48f, size * 0.018f, lensHighlightPaint)

    canvas.drawCircle(size * 0.61f, size * 0.63f, size * 0.022f, bodyPaint)
    canvas.drawLine(size * 0.61f, size * 0.65f, size * 0.69f, size * 0.74f, detailPaint)
    canvas.drawLine(size * 0.69f, size * 0.74f, size * 0.69f, size * 0.82f, detailPaint)
    canvas.drawLine(size * 0.61f, size * 0.82f, size * 0.75f, size * 0.82f, detailPaint)
}

private fun drawStreetBenchSymbol(canvas: Canvas, size: Int, symbolColor: Int) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = symbolColor
        style = Paint.Style.STROKE
        strokeWidth = size * 0.06f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(symbolColor, 38)
        style = Paint.Style.FILL
    }

    val backRect = RectF(
        size * 0.24f,
        size * 0.30f,
        size * 0.76f,
        size * 0.42f
    )
    val seatRect = RectF(
        size * 0.20f,
        size * 0.49f,
        size * 0.80f,
        size * 0.60f
    )

    canvas.drawRoundRect(backRect, size * 0.04f, size * 0.04f, fillPaint)
    canvas.drawRoundRect(seatRect, size * 0.04f, size * 0.04f, fillPaint)
    canvas.drawRoundRect(backRect, size * 0.04f, size * 0.04f, strokePaint)
    canvas.drawRoundRect(seatRect, size * 0.04f, size * 0.04f, strokePaint)
    canvas.drawLine(size * 0.32f, size * 0.42f, size * 0.32f, size * 0.60f, strokePaint)
    canvas.drawLine(size * 0.68f, size * 0.42f, size * 0.68f, size * 0.60f, strokePaint)
    canvas.drawLine(size * 0.34f, size * 0.60f, size * 0.28f, size * 0.80f, strokePaint)
    canvas.drawLine(size * 0.66f, size * 0.60f, size * 0.72f, size * 0.80f, strokePaint)
}

private fun drawEscalatorSymbol(canvas: Canvas, size: Int) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val stepsPath = Path().apply {
        moveTo(size * 0.28f, size * 0.64f)
        lineTo(size * 0.38f, size * 0.64f)
        lineTo(size * 0.38f, size * 0.54f)
        lineTo(size * 0.50f, size * 0.54f)
        lineTo(size * 0.50f, size * 0.44f)
        lineTo(size * 0.62f, size * 0.44f)
    }
    canvas.drawPath(stepsPath, strokePaint)

    canvas.drawLine(size * 0.32f, size * 0.32f, size * 0.66f, size * 0.32f, strokePaint)
    canvas.drawLine(size * 0.58f, size * 0.24f, size * 0.66f, size * 0.32f, strokePaint)
    canvas.drawLine(size * 0.58f, size * 0.40f, size * 0.66f, size * 0.32f, strokePaint)
}

private fun drawThermometerSymbol(canvas: Canvas, size: Int) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    val tubeBounds = RectF(
        size * 0.42f,
        size * 0.24f,
        size * 0.52f,
        size * 0.68f
    )
    canvas.drawRoundRect(tubeBounds, size * 0.06f, size * 0.06f, strokePaint)
    canvas.drawCircle(size * 0.47f, size * 0.73f, size * 0.11f, strokePaint)
    canvas.drawLine(size * 0.47f, size * 0.54f, size * 0.47f, size * 0.73f, strokePaint)
    canvas.drawCircle(size * 0.47f, size * 0.73f, size * 0.05f, fillPaint)

    val tickStartX = size * 0.58f
    val tickBaseY = size * 0.30f
    val tickGap = size * 0.08f
    repeat(5) { index ->
        val y = tickBaseY + index * tickGap
        val tickLength = if (index % 2 == 0) size * 0.13f else size * 0.09f
        canvas.drawLine(
            tickStartX,
            y,
            tickStartX + tickLength,
            y,
            strokePaint
        )
    }
}

private fun withAlpha(color: Int, alpha: Int): Int {
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color)
    )
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
