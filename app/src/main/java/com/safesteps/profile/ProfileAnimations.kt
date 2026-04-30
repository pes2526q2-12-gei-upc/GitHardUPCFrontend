package com.safesteps.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safesteps.data.PremiResponse
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LevelUpAnimationOverlay(
    level: Long,
    onDismiss: () -> Unit
) {
    val scaleAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(300))
        scaleAnim.animateTo(
            1.2f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        scaleAnim.animateTo(
            1f,
            animationSpec = tween(200, easing = FastOutSlowInEasing)
        )
        textAlpha.animateTo(1f, animationSpec = tween(300))
        delay(2500)
        alphaAnim.animateTo(0f, animationSpec = tween(400))
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alphaAnim.value)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* block touches */ },
        contentAlignment = Alignment.Center
    ) {
        // Glow ring behind
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(glowScale.value)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = 0.4f),
                            Color(0xFFFFD700).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scaleAnim.value)
        ) {
            // Star/emoji
            Text(
                text = "⭐",
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LEVEL UP!",
                color = Color(0xFFFFD700),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Level $level",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }

        // Floating particles
        repeat(8) { index ->
            val particleAnim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(index * 80L)
                particleAnim.animateTo(
                    1f,
                    animationSpec = tween(1200, easing = LinearEasing)
                )
            }
            val angle = (index * 45f) * (Math.PI / 180f)
            val radius = 120.dp * particleAnim.value

            Text(
                text = listOf("✨", "🌟", "💫", "⚡")[index % 4],
                fontSize = 20.sp,
                modifier = Modifier
                    .offset(
                        x = (radius.value * cos(angle)).dp,
                        y = (radius.value * sin(angle)).dp
                    )
                    .alpha((1f - particleAnim.value).coerceAtLeast(0f))
            )
        }
    }
}

@Composable
fun PrizeAnimationOverlay(
    prize: PremiResponse,
    onDismiss: () -> Unit
) {
    val scaleAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(0f) }
    val colorAlpha = remember { Animatable(0f) }

    val prizeColor = parseColorFromPremiId(prize.id) ?: Color(0xFF2196F3)

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(300))
        scaleAnim.animateTo(
            1.3f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
        scaleAnim.animateTo(
            1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
        colorAlpha.animateTo(1f, animationSpec = tween(400))
        delay(2200)
        alphaAnim.animateTo(0f, animationSpec = tween(400))
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alphaAnim.value)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* block touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scaleAnim.value)
        ) {
            Text(
                text = "🎉",
                fontSize = 56.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "New color unlocked!",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Color circle preview
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .alpha(colorAlpha.value)
                    .scale(colorAlpha.value)
                    .background(prizeColor, CircleShape)
            )
        }
    }
}

private fun parseColorFromPremiId(id: String?): Color? {
    if (id == null) return null
    val regex = Regex("R(\\d+)G(\\d+)B(\\d+)")
    val match = regex.find(id) ?: return null
    val r = match.groupValues[1].toIntOrNull() ?: return null
    val g = match.groupValues[2].toIntOrNull() ?: return null
    val b = match.groupValues[3].toIntOrNull() ?: return null
    return Color(r, g, b)
}