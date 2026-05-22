package com.safesteps.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safesteps.data.PremiResponse
import com.safesteps.i18n.appString
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import com.safesteps.R


enum class PrizeRarity {
    COMMON, RARE, EPIC, LEGENDARY;

    companion object {
        fun fromString(value: String?) =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: COMMON

        fun fromPremi(premi: PremiResponse): PrizeRarity = fromString(premi.oddity)
    }
}


val PrizeRarity.rarityColor: Color get() = when (this) {
    PrizeRarity.COMMON -> Color(0xFF4CAF50)
    PrizeRarity.RARE -> Color(0xFF42A5F5)
    PrizeRarity.EPIC -> Color(0xFFCE93D8)
    PrizeRarity.LEGENDARY -> Color(0xFFFFD700)
}

// Paletas completas por rareza — todas vibrantes, sin negro dominante
private val commonPalette = listOf(
    Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C),
    Color(0xFF43A047), Color(0xFF4CAF50), Color(0xFF66BB6A),
    Color(0xFF81C784), Color(0xFFA5D6A7)
)
private val rarePalette = listOf(
    Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2),
    Color(0xFF1E88E5), Color(0xFF2196F3), Color(0xFF42A5F5),
    Color(0xFF64B5F6), Color(0xFF90CAF9)
)
private val epicPalette = listOf(
    Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFF7B1FA2),
    Color(0xFF8E24AA), Color(0xFF9C27B0), Color(0xFFAB47BC),
    Color(0xFFCE93D8), Color(0xFFE1BEE7)
)
private val legendaryPalette = listOf(
    Color(0xFF3E2723), Color(0xFF5D4037), Color(0xFF795548),
    Color(0xFFBF8600), Color(0xFFE6A817), Color(0xFFFFBF00),
    Color(0xFFFFD700), Color(0xFFFFEE58)
)

fun PrizeRarity.palette() = when (this) {
    PrizeRarity.COMMON -> commonPalette
    PrizeRarity.RARE -> rarePalette
    PrizeRarity.EPIC -> epicPalette
    PrizeRarity.LEGENDARY -> legendaryPalette
}

// ─── Ruleta ───────────────────────────────────────────────────────────────────

private fun buildInterleavedSegments(): List<PrizeRarity> {
    val result = mutableListOf<PrizeRarity>()
    val common = 20; val rare = 8; val epic = 4; val legendary = 1
    val total = common + rare + epic + legendary
    var rareAcc = 0f; var epicAcc = 0f; var legendaryAcc = 0f
    for (i in 0 until total) {
        rareAcc += rare.toFloat() / common
        epicAcc += epic.toFloat() / common
        legendaryAcc += legendary.toFloat() / common
        when {
            legendaryAcc >= 1f -> { result.add(PrizeRarity.LEGENDARY); legendaryAcc -= 1f }
            epicAcc >= 1f -> { result.add(PrizeRarity.EPIC); epicAcc -= 1f }
            rareAcc >= 1f -> { result.add(PrizeRarity.RARE); rareAcc -= 1f }
            else -> result.add(PrizeRarity.COMMON)
        }
    }
    return result
}

private data class WheelSegment(val rarity: PrizeRarity, val startAngle: Float, val sweepAngle: Float)

private fun buildWheelSegments(): List<WheelSegment> {
    val slots = buildInterleavedSegments()
    val sweepPerSlot = 360f / slots.size
    return slots.mapIndexed { i, rarity -> WheelSegment(rarity, i * sweepPerSlot, sweepPerSlot) }
}

private fun targetRotationForRarity(rarity: PrizeRarity, segments: List<WheelSegment>): Float {
    val seg = segments.filter { it.rarity == rarity }.randomOrNull() ?: return 0f
    return 270f - (seg.startAngle + seg.sweepAngle / 2f)
}

@Composable
private fun RouletteWheel(rotation: Float, modifier: Modifier = Modifier) {
    val segments = remember { buildWheelSegments() }
    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(rotation, center.x, center.y)

        segments.forEach { seg ->
            drawArc(
                color = seg.rarity.rarityColor,
                startAngle = seg.startAngle, sweepAngle = seg.sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            drawArc(
                color = Color.Black.copy(alpha = 0.18f),
                startAngle = seg.startAngle, sweepAngle = seg.sweepAngle * 0.4f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = seg.startAngle + seg.sweepAngle * 0.6f,
                sweepAngle = seg.sweepAngle * 0.4f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            drawArc(
                color = Color.Black.copy(alpha = 0.5f),
                startAngle = seg.startAngle, sweepAngle = seg.sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 1.2f)
            )
        }

        // Anillo exterior brillante
        drawCircle(color = Color.White.copy(alpha = 0.3f), radius = radius, center = center, style = Stroke(width = 5f))
        drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius - 3f, center = center, style = Stroke(width = 2f))

        // Centro
        drawCircle(color = Color(0xFF222222), radius = radius * 0.13f, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.35f), radius = radius * 0.07f, center = center)

        drawContext.canvas.nativeCanvas.restore()

        // Flecha indicadora (no rota)
        val aSize = radius * 0.18f
        val tip = center.y - radius + 4f
        val path = Path().apply {
            moveTo(center.x, tip)
            lineTo(center.x - aSize * 0.55f, tip - aSize * 1.1f)
            lineTo(center.x + aSize * 0.55f, tip - aSize * 1.1f)
            close()
        }
        drawPath(path, color = Color.White)
        drawPath(path, color = Color(0xFFFFD700).copy(alpha = 0.8f), style = Stroke(width = 2f))
    }
}

@Composable
private fun WheelBackground(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "wbg")
    val t = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1A3A5C), Color(0xFF0D2137), Color(0xFF050F1A)),
                center = Offset(size.width / 2, size.height / 2), radius = size.width
            )
        )
        // Ondas de agua azuladas
        for (i in 0..5) {
            val phase = (i * 0.18f + t.value) % 1f
            val r = size.width * 0.5f + size.width * 0.8f * phase
            drawCircle(color = Color(0xFF42A5F5).copy(alpha = (1f - phase) * 0.18f), radius = r,
                center = Offset(size.width / 2, size.height / 2), style = Stroke(width = 2f))
        }
        // Partículas
        val positions = listOf(0.15f to 0.2f, 0.5f to 0.1f, 0.8f to 0.3f, 0.3f to 0.7f, 0.7f to 0.8f, 0.9f to 0.6f)
        positions.forEachIndexed { idx, (px, py) ->
            val yOff = (t.value * 0.3f + idx * 0.17f) % 1f
            val pos = Offset(size.width * px, size.height * ((py + yOff) % 1f))
            drawCircle(color = Color(0xFF90CAF9).copy(alpha = 0.7f), radius = 3.5f, center = pos)
            drawCircle(color = Color(0xFF90CAF9).copy(alpha = 0.2f), radius = 10f, center = pos)
        }
    }
}

// ─── Fondos de resultado por rareza ──────────────────────────────────────────

@Composable
private fun CommonBackground(t: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val pal = commonPalette
        // Base: gradiente verde rico
        drawRect(brush = Brush.radialGradient(
            colors = listOf(pal[4], pal[2], pal[0]),
            center = Offset(size.width / 2, size.height / 2), radius = size.width
        ))
        // Ondas concéntricas animadas en varios tonos verdes
        for (i in 0..10) {
            val phase = ((i * 0.09f + t) % 1f)
            val r = size.width * 1.4f * phase
            val color = pal[i % pal.size].copy(alpha = (1f - phase) * 0.45f)
            drawCircle(color = color, radius = r,
                center = Offset(size.width / 2, size.height / 2), style = Stroke(width = 12f))
        }
        // Puntos de brillo dispersos
        for (i in 0..14) {
            val angle = (i * 137.5f) * PI.toFloat() / 180f
            val d = size.width * 0.35f * ((i % 3 + 1) / 3f)
            val cx = size.width / 2 + d * cos(angle)
            val cy = size.height / 2 + d * sin(angle)
            val pulse = abs(sin((t * 3 + i * 0.5f) * PI.toFloat()))
            drawCircle(color = pal[6].copy(alpha = 0.3f + 0.3f * pulse), radius = 8f + 6f * pulse, center = Offset(cx, cy))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRareBase() {
    drawRect(brush = Brush.radialGradient(
        colors = listOf(rarePalette[5], rarePalette[3], rarePalette[1]),
        center = Offset(size.width / 2, size.height / 2), radius = size.width
    ))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRareHexagonRow(gy: Float, row: Int, t: Float, hexW: Float, hexR: Float) {
    var gx = if (row % 2 == 0) -hexW / 2 else -hexW
    while (gx < size.width + hexW) {
        val colorIdx = ((row * 3 + (gx / hexW).toInt()) and 0x7FFFFFFF) % rarePalette.size
        val rotOff = t * 5f
        val path = Path().apply {
            for (k in 0..5) {
                val a = (k * 60f + rotOff) * PI.toFloat() / 180f
                val px = gx + hexR * cos(a)
                val py = gy + hexR * sin(a)
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(path, color = rarePalette[colorIdx].copy(alpha = 0.25f))
        drawPath(path, color = rarePalette[(colorIdx + 2) % rarePalette.size].copy(alpha = 0.6f), style = Stroke(width = 1.5f))
        gx += hexW
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRareHexagons(t: Float) {
    val hexR = 55f
    val hexW = hexR * 1.732f
    val hexH = hexR * 2f
    var row = 0
    var gy = -hexH
    while (gy < size.height + hexH) {
        drawRareHexagonRow(gy, row, t, hexW, hexR)
        gy += hexH * 0.75f
        row++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRareBolts(t: Float) {
    for (i in 0..4) {
        val xPos = size.width * (i + 1) / 6f
        val segH = size.height / 6f
        for (j in 0..5) {
            val y1 = j * segH
            val y2 = y1 + segH
            val midX = xPos + (sin((t * 4 + i + j).toFloat() * PI.toFloat()) * 18f)
            val bolt = Path().apply {
                moveTo(xPos, y1)
                lineTo(midX, (y1 + y2) / 2)
                lineTo(xPos, y2)
            }
            drawPath(bolt, color = rarePalette[6].copy(alpha = 0.35f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun RareBackground(t: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRareBase()
        drawRareHexagons(t)
        drawRareBolts(t)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEpicBase() {
    drawRect(brush = Brush.radialGradient(
        colors = listOf(epicPalette[5], epicPalette[3], epicPalette[1]),
        center = Offset(size.width / 2, size.height / 2), radius = size.width
    ))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEpicSquareRow(gy: Float, row: Int, t: Float, step: Float) {
    var gx = if (row % 2 == 0) -step / 2 else -step
    while (gx < size.width + step) {
        val colorIdx = ((row * 4 + (gx / step).toInt()) and 0x7FFFFFFF) % epicPalette.size
        val baseRot = t * 360f * 0.08f + row * 22f + (gx / step) * 17f
        val rot = baseRot * PI.toFloat() / 180f
        val hs = step * 0.4f
        val corners = listOf(
            Offset(-hs, -hs), Offset(hs, -hs), Offset(hs, hs), Offset(-hs, hs)
        ).map { c ->
            Offset(gx + c.x * cos(rot) - c.y * sin(rot), gy + c.x * sin(rot) + c.y * cos(rot))
        }
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, color = epicPalette[colorIdx].copy(alpha = 0.28f))
        drawPath(path, color = epicPalette[(colorIdx + 3) % epicPalette.size].copy(alpha = 0.55f), style = Stroke(width = 1.5f))
        gx += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEpicSquares(t: Float) {
    val step = 70f
    var gy = -step
    var row = 0
    while (gy < size.height + step) {
        drawEpicSquareRow(gy, row, t, step)
        gy += step * 0.85f
        row++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEpicStars(t: Float) {
    for (i in 0..8) {
        val angle = (i * 137.5f + t * 40f) * PI.toFloat() / 180f
        val dist = size.width * 0.38f * ((i % 3 + 1f) / 3f)
        val cx = size.width / 2 + dist * cos(angle)
        val cy = size.height / 2 + dist * sin(angle)
        val pulse = 0.5f + 0.5f * abs(sin((t * 2.5f + i * 0.7f) * PI.toFloat()))
        val sr = 14f + 10f * pulse
        val starPath = Path().apply {
            for (k in 0..11) {
                val a = (k * 30f) * PI.toFloat() / 180f
                val r = if (k % 2 == 0) sr else sr * 0.4f
                val px = cx + r * cos(a)
                val py = cy + r * sin(a)
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(starPath, color = epicPalette[6].copy(alpha = 0.5f + 0.3f * pulse))
    }
}

@Composable
private fun EpicBackground(t: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawEpicBase()
        drawEpicSquares(t)
        drawEpicStars(t)
    }
}

@Composable
private fun LegendaryBackground(t: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val pal = legendaryPalette
        // Base: gradiente dorado ámbar que llena toda la pantalla
        drawRect(brush = Brush.radialGradient(
            colors = listOf(pal[6], pal[4], pal[2]),
            center = Offset(size.width / 2, size.height / 2), radius = size.width
        ))
        // Diamantes intercalados que llenan toda la pantalla
        val dStep = 65f
        var gy = -dStep
        var row = 0
        while (gy < size.height + dStep) {
            var gx = if (row % 2 == 0) -dStep / 2 else -dStep
            while (gx < size.width + dStep) {
                val colorIdx = ((row * 5 + (gx / dStep).toInt()) and 0x7FFFFFFF) % pal.size
                val pulse = 0.7f + 0.3f * abs(sin((t * 2.8f + row * 0.4f + gx / dStep * 0.3f) * PI.toFloat()))
                val ds = dStep * 0.42f * pulse
                val path = Path().apply {
                    moveTo(gx, gy - ds)
                    lineTo(gx + ds * 0.65f, gy)
                    lineTo(gx, gy + ds)
                    lineTo(gx - ds * 0.65f, gy)
                    close()
                }
                drawPath(path, color = pal[colorIdx].copy(alpha = 0.35f))
                drawPath(path, color = pal[(colorIdx + 3) % pal.size].copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                gx += dStep
            }
            gy += dStep * 0.85f
            row++
        }
        // Rayos de luz desde el centro
        for (i in 0..11) {
            val angle = (i * 30f + t * 25f) * PI.toFloat() / 180f
            val len = size.width * (0.4f + 0.4f * abs(sin((t * 1.5f + i * 0.8f) * PI.toFloat())))
            val cx = size.width / 2f; val cy = size.height / 2f
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + len * cos(angle), cy + len * sin(angle))
            }
            drawPath(path, color = pal[6].copy(alpha = 0.22f), style = Stroke(width = 3f + 2f * abs(sin((t + i * 0.3f).toFloat() * PI.toFloat())), cap = StrokeCap.Round))
        }
        // Destellos extras pulsantes
        for (i in 0..15) {
            val a = (i * 137.5f + t * 60f) * PI.toFloat() / 180f
            val d = size.width * 0.42f * ((i % 4 + 1f) / 4f)
            val cx = size.width / 2 + d * cos(a)
            val cy = size.height / 2 + d * sin(a)
            val p = abs(sin((t * 3.5f + i * 0.6f) * PI.toFloat()))
            drawCircle(color = pal[7].copy(alpha = 0.5f * p), radius = 5f + 8f * p, center = Offset(cx, cy))
        }
    }
}

// ─── Partículas por rareza ────────────────────────────────────────────────────

@Composable
private fun ParticlesEffect(rarity: PrizeRarity) {
    val count = when (rarity) { PrizeRarity.COMMON -> 6; PrizeRarity.RARE -> 12; PrizeRarity.EPIC -> 16; PrizeRarity.LEGENDARY -> 22 }
    val emojis = when (rarity) {
        PrizeRarity.COMMON -> listOf("✦", "🌿", "🍀", "✧", "🌱")
        PrizeRarity.RARE -> listOf("💧", "❄️", "✦", "⚡", "💎", "★")
        PrizeRarity.EPIC -> listOf("✨", "💫", "🔮", "⚡", "🌀", "★")
        PrizeRarity.LEGENDARY -> listOf("👑", "🌟", "✨", "💫", "🔥", "⭐", "💛")
    }
    repeat(count) { index ->
        val anim = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(index * 50L)
            anim.animateTo(1f, animationSpec = tween(1800, easing = LinearEasing))
        }
        val angle = (index * (360f / count)) * (PI / 180.0)
        val radius = when (rarity) {
            PrizeRarity.COMMON -> 140.dp; PrizeRarity.RARE -> 165.dp
            PrizeRarity.EPIC -> 185.dp; PrizeRarity.LEGENDARY -> 210.dp
        } * anim.value
        Text(
            text = emojis[index % emojis.size],
            fontSize = when (rarity) { PrizeRarity.COMMON -> 16.sp; PrizeRarity.RARE -> 20.sp; PrizeRarity.EPIC -> 24.sp; PrizeRarity.LEGENDARY -> 30.sp },
            modifier = Modifier
                .offset(x = (radius.value * cos(angle)).dp, y = (radius.value * sin(angle)).dp)
                .alpha((1f - anim.value).coerceAtLeast(0f))
        )
    }
}

// ─── Glow ring ────────────────────────────────────────────────────────────────

@Composable
private fun GlowRing(rarity: PrizeRarity) {
    val inf = rememberInfiniteTransition(label = "glow")
    val gs = inf.animateFloat(1f,
        when (rarity) { PrizeRarity.COMMON -> 1.1f; PrizeRarity.RARE -> 1.2f; PrizeRarity.EPIC -> 1.35f; PrizeRarity.LEGENDARY -> 1.55f },
        infiniteRepeatable(tween(when (rarity) { PrizeRarity.COMMON -> 1600; PrizeRarity.RARE -> 1100; PrizeRarity.EPIC -> 750; PrizeRarity.LEGENDARY -> 450 }, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gs")
    Box(modifier = Modifier.size(260.dp).scale(gs.value).background(
        brush = Brush.radialGradient(colors = listOf(
            rarity.rarityColor.copy(alpha = when (rarity) { PrizeRarity.COMMON -> 0.4f; PrizeRarity.RARE -> 0.5f; PrizeRarity.EPIC -> 0.65f; PrizeRarity.LEGENDARY -> 0.85f }),
            rarity.rarityColor.copy(alpha = 0.15f), Color.Transparent
        )), shape = CircleShape
    ).blur(radius = when (rarity) { PrizeRarity.COMMON -> 8.dp; PrizeRarity.RARE -> 16.dp; PrizeRarity.EPIC -> 24.dp; PrizeRarity.LEGENDARY -> 36.dp }))
}

// ─── Overlay principal ────────────────────────────────────────────────────────

@Composable
private fun OpeningRewardView(wheelRotation: Float) {
    WheelBackground(modifier = Modifier.fillMaxSize())
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = appString(R.string.prize_roulette_title),
                color = Color(0xFF90CAF9),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = appString(R.string.prize_roulette_subtitle),
                color = Color(0xFF90CAF9).copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(300.dp).background(
                brush = Brush.radialGradient(colors = listOf(Color(0xFF2196F3).copy(alpha = 0.12f), Color.Transparent)), shape = CircleShape))
            RouletteWheel(rotation = wheelRotation, modifier = Modifier.size(260.dp))
        }
    }
}

@Composable
private fun PrizeResultView(
    prize: PremiResponse,
    rarity: PrizeRarity,
    patternT: Float,
    showParticles: Boolean,
    resultScale: Float,
    resultAlpha: Float
) {
    when (rarity) {
        PrizeRarity.COMMON -> CommonBackground(patternT)
        PrizeRarity.RARE -> RareBackground(patternT)
        PrizeRarity.EPIC -> EpicBackground(patternT)
        PrizeRarity.LEGENDARY -> LegendaryBackground(patternT)
    }

    Box(contentAlignment = Alignment.Center) {
        GlowRing(rarity = rarity)
        if (showParticles) ParticlesEffect(rarity = rarity)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(resultScale).alpha(resultAlpha).padding(horizontal = 24.dp)
        ) {
            PrizeResultHeader(prize, rarity)
            Spacer(modifier = Modifier.height(26.dp))
            PrizeVisualizer(prize, rarity)
        }

        if (rarity == PrizeRarity.LEGENDARY) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                appString(R.string.prize_incredibly_rare), color = Color(0xFFFFD700),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun PrizeResultHeader(prize: PremiResponse, rarity: PrizeRarity) {
    Text(
        text = when (rarity) {
            PrizeRarity.COMMON -> "🎁"; PrizeRarity.RARE -> "💎"
            PrizeRarity.EPIC -> "🔮"; PrizeRarity.LEGENDARY -> "👑"
        },
        fontSize = when (rarity) {
            PrizeRarity.COMMON -> 54.sp; PrizeRarity.RARE -> 62.sp
            PrizeRarity.EPIC -> 70.sp; PrizeRarity.LEGENDARY -> 80.sp
        }
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = when (rarity) {
            PrizeRarity.COMMON -> appString(R.string.prize_rarity_common)
            PrizeRarity.RARE -> appString(R.string.prize_rarity_rare)
            PrizeRarity.EPIC -> appString(R.string.prize_rarity_epic)
            PrizeRarity.LEGENDARY -> appString(R.string.prize_rarity_legendary)
        }.uppercase(),
        color = rarity.rarityColor,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        appString(R.string.prize_unlocked), color = Color.White.copy(alpha = 0.95f),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = prize.name ?: appString(R.string.prize_mystery),
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PrizeVisualizer(prize: PremiResponse, rarity: PrizeRarity) {
    when {
        prize.name == "XP" -> {
            Text("⭐", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = appString(R.string.prize_duplicate_title),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = appString(R.string.prize_duplicate_subtitle),
                color = Color(0xFFFFD700),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        else -> when (prizeTypeFromId(prize.id)) {
            PrizeType.AVATAR -> {
                val url = prize.url
                if (url != null && url != "NONE") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp).background(
                            brush = Brush.radialGradient(
                                colors = listOf(rarity.rarityColor.copy(alpha = 0.6f), Color.Transparent)
                            ), shape = CircleShape
                        )
                    ) {
                        coil.compose.AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.size(78.dp).clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                } else {
                    Text("🧑", fontSize = 64.sp)
                }
            }
            PrizeType.COLOR -> {
                val brush = patternBrushFromId(prize.id)
                val solidColor = parseColorFromPremiId(prize.id)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp).background(
                        brush = Brush.radialGradient(
                            colors = listOf(rarity.rarityColor.copy(alpha = 0.6f), Color.Transparent)
                        ), shape = CircleShape
                    )
                ) {
                    if (isRgbFluid(prize.id)) {
                        RgbFluidCircle(modifier = Modifier.size(78.dp))
                    } else if (brush != null) {
                        Box(modifier = Modifier.size(78.dp).clip(CircleShape).background(brush))
                    } else if (solidColor != null) {
                        Box(modifier = Modifier.size(78.dp).background(solidColor, CircleShape))
                    }
                }
            }
            PrizeType.LABEL -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp).background(
                        brush = Brush.radialGradient(
                            colors = listOf(rarity.rarityColor.copy(alpha = 0.6f), Color.Transparent)
                        ), shape = CircleShape
                    )
                ) {
                    Text("🏷️", fontSize = 48.sp)
                }
            }
        }
    }
}

@Composable
internal fun RgbFluidCircle(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "rgb")
    val t = inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val neonColors = listOf(
        Color(0xFF00FFFF), Color(0xFF00FF88), Color(0xFF0088FF),
        Color(0xFF8800FF), Color(0xFFFF00FF), Color(0xFF00FFFF)
    )
    val shiftedColors = remember(t.value) {
        val offset = (t.value * (neonColors.size - 1)).toInt()
        neonColors.drop(offset) + neonColors.take(offset)
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.sweepGradient(shiftedColors))
    )
}

@Composable
fun PrizeAnimationOverlay(prize: PremiResponse, onDismiss: () -> Unit) {
    val rarity = remember { PrizeRarity.fromPremi(prize) }
    val segments = remember { buildWheelSegments() }
    val targetAngle = remember { targetRotationForRarity(rarity, segments) }

    val overlayAlpha = remember { Animatable(0f) }
    val wheelRotation = remember { Animatable(0f) }
    val resultScale = remember { Animatable(0f) }
    val resultAlpha = remember { Animatable(0f) }
    var showResult by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }

    val inf = rememberInfiniteTransition(label = "bg")
    val patternT = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), label = "pt")

    LaunchedEffect(Unit) {
        overlayAlpha.animateTo(1f, animationSpec = tween(400))
        wheelRotation.animateTo(6 * 360f + targetAngle, animationSpec = tween(4200, easing = CubicBezierEasing(0.12f, 0f, 0.0f, 1f)))
        delay(350)
        showResult = true; showParticles = true
        resultScale.animateTo(1.3f, animationSpec = tween(380, easing = FastOutSlowInEasing))
        resultScale.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        resultAlpha.animateTo(1f, animationSpec = tween(380))
        delay(when (rarity) { PrizeRarity.COMMON -> 2200L; PrizeRarity.RARE -> 2800L; PrizeRarity.EPIC -> 3400L; PrizeRarity.LEGENDARY -> 4200L })
        overlayAlpha.animateTo(0f, animationSpec = tween(600))
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize().alpha(overlayAlpha.value)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center
    ) {
        if (!showResult) {
            OpeningRewardView(wheelRotation = wheelRotation.value)
        } else {
            PrizeResultView(
                prize = prize,
                rarity = rarity,
                patternT = patternT.value,
                showParticles = showParticles,
                resultScale = resultScale.value,
                resultAlpha = resultAlpha.value,
            )
        }
    }
}

// ─── Level Up ─────────────────────────────────────────────────────────────────

@Composable
fun LevelUpAnimationOverlay(level: Long, onDismiss: () -> Unit) {
    val scaleAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val inf = rememberInfiniteTransition(label = "glow")
    val gs = inf.animateFloat(1f, 1.35f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gs")

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(300))
        scaleAnim.animateTo(1.2f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        scaleAnim.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, animationSpec = tween(300))
        delay(2500)
        alphaAnim.animateTo(0f, animationSpec = tween(400))
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize().alpha(alphaAnim.value).background(Color.Black.copy(alpha = 0.78f))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(210.dp).scale(gs.value).background(
            brush = Brush.radialGradient(colors = listOf(Color(0xFFFFD700).copy(alpha = 0.5f), Color(0xFFFFD700).copy(alpha = 0.1f), Color.Transparent)), shape = CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scaleAnim.value)) {
            Text("⭐", fontSize = 70.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text("LEVEL UP!", color = Color(0xFFFFD700), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.alpha(textAlpha.value))
            Spacer(modifier = Modifier.height(6.dp))
            Text("Level $level", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.alpha(textAlpha.value))
        }
        repeat(8) { index ->
            val pa = remember { Animatable(0f) }
            LaunchedEffect(Unit) { delay(index * 80L); pa.animateTo(1f, animationSpec = tween(1200, easing = LinearEasing)) }
            val angle = (index * 45f) * (PI / 180.0)
            val r = 135.dp * pa.value
            Text(listOf("✨", "🌟", "💫", "⚡")[index % 4], fontSize = 22.sp,
                modifier = Modifier.offset(x = (r.value * cos(angle)).dp, y = (r.value * sin(angle)).dp).alpha((1f - pa.value).coerceAtLeast(0f)))
        }
    }
}


// ─── Prize type detection ─────────────────────────────────────────────────────

enum class PrizeType { AVATAR, COLOR, LABEL }

fun prizeTypeFromId(id: String?): PrizeType = when {
    id == null -> PrizeType.AVATAR
    id.startsWith("A") -> PrizeType.AVATAR
    id.startsWith("R") -> PrizeType.COLOR
    id.startsWith("T") -> PrizeType.LABEL
    else -> PrizeType.AVATAR
}

internal fun parseColorFromPremiId(id: String?): Color? {
    if (id == null) return null
    // Formato: R001_RTCOL_R0G255B0  → extrae R0G255B0
    val regex = Regex("_R(\\d+)G(\\d+)B(\\d+)")
    val match = regex.find(id) ?: return null
    val r = match.groupValues[1].toIntOrNull() ?: return null
    val g = match.groupValues[2].toIntOrNull() ?: return null
    val b = match.groupValues[3].toIntOrNull() ?: return null
    return Color(r, g, b)
}

// Patrones especiales EPIC / LEGENDARY
fun patternBrushFromId(id: String?): Brush? = when {
    id == null -> null
    id.contains("BLAUGRANA") -> Brush.linearGradient(
        colors = listOf(Color(0xFF004D98), Color(0xFFA50044))
    )
    id.contains("TAXI") -> Brush.linearGradient(
        colors = listOf(Color(0xFFFFCC00), Color(0xFF1A1A1A), Color(0xFFFFCC00))
    )
    id.contains("NIT") -> Brush.linearGradient(
        colors = listOf(Color(0xFF0A0A1A), Color(0xFF1B1B3A), Color(0xFF2E2E5E), Color(0xFF0A0A1A))
    )
    id.contains("RGB_FLUID") -> null // animado, se gestiona aparte
    else -> null
}

fun isRgbFluid(id: String?) = id?.contains("RGB_FLUID") == true
