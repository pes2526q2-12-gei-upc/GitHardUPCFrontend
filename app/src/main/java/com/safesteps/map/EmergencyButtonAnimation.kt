package com.safesteps.map

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun rememberEmergencyHeartbeatModifier(isActive: Boolean): Modifier {
    if (!isActive) {
        return Modifier
    }

    val transition = rememberInfiniteTransition(label = "emergencyHeartbeat")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1f at 0
                1.12f at 160
                0.96f at 300
                1.16f at 460
                1f at 680
                1f at 1400
            }
        ),
        label = "emergencyHeartbeatScale"
    )

    return Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
