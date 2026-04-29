package com.safesteps.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.safesteps.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

private const val DefaultNotificationDurationMillis = 3_000L
private const val MinimumNotificationDurationMillis = 1_000L
private const val ExitAnimationDurationMillis = 220L

private val NotificationShape = RoundedCornerShape(28.dp)
private val NotificationCardGradient = listOf(
    Color(0xFFF8FCF8),
    Color(0xFFE8F3EC),
    Color(0xFFF6EFE6)
)
private val NotificationGlowGradient = listOf(
    Color(0x33B5E2CF),
    Color(0x00000000)
)
private val NotificationAccentGradient = listOf(
    Color(0xFF0F766E),
    Color(0xFF56B28F)
)
private val NotificationLogoBackground = Color(0xFF011514)
private val NotificationTextPrimary = Color(0xFF173A35)
private val NotificationTextSecondary = Color(0xFF58716A)
private val NotificationBorder = Color(0xCCFFFFFF)
private val NotificationTrack = Color(0x261A6A5A)

data class ScreenNotification(
    val id: Long,
    val name: String,
    val text: String,
    val durationMillis: Long
)

private enum class NotificationDismissMode {
    AUTO,
    USER
}

object ScreenNotificationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val nextId = AtomicLong(1L)
    private val pendingNotifications = ArrayDeque<ScreenNotification>()
    private val _currentNotification = MutableStateFlow<ScreenNotification?>(null)

    val currentNotification: StateFlow<ScreenNotification?> = _currentNotification.asStateFlow()

    fun showNotification(
        notificationName: String,
        text: String,
        durationMillis: Long = DefaultNotificationDurationMillis
    ) {
        if (notificationName.isBlank() || text.isBlank()) {
            return
        }

        val notification = ScreenNotification(
            id = nextId.getAndIncrement(),
            name = notificationName.trim(),
            text = text.trim(),
            durationMillis = durationMillis.coerceAtLeast(MinimumNotificationDurationMillis)
        )

        scope.launch {
            mutex.withLock {
                if (_currentNotification.value == null) {
                    _currentNotification.value = notification
                } else {
                    pendingNotifications.addLast(notification)
                }
            }
        }
    }

    fun dismissCurrentNotification(expectedId: Long? = null) {
        scope.launch {
            mutex.withLock {
                val currentNotification = _currentNotification.value ?: return@withLock
                if (expectedId != null && currentNotification.id != expectedId) {
                    return@withLock
                }

                _currentNotification.value = pendingNotifications.removeFirstOrNull()
            }
        }
    }
}

@Composable
fun ScreenNotificationHost(
    modifier: Modifier = Modifier
) {
    val currentNotification by ScreenNotificationManager.currentNotification.collectAsState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        currentNotification?.let { notification ->
            key(notification.id) {
                DismissibleScreenNotification(
                    notification = notification,
                    onDismiss = {
                        ScreenNotificationManager.dismissCurrentNotification(notification.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun DismissibleScreenNotification(
    notification: ScreenNotification,
    onDismiss: () -> Unit
) {
    val progress = remember(notification.id) { Animatable(1f) }
    val dragOffset = remember(notification.id) { Animatable(0f) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dismissThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    var isVisible by remember(notification.id) { mutableStateOf(false) }
    var dismissalRequested by remember(notification.id) { mutableStateOf(false) }
    var dismissMode by remember(notification.id) { mutableStateOf(NotificationDismissMode.AUTO) }

    fun requestDismiss(mode: NotificationDismissMode) {
        if (dismissalRequested) {
            return
        }

        dismissMode = mode
        dismissalRequested = true
        if (mode == NotificationDismissMode.AUTO) {
            isVisible = false
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(notification.id) {
        isVisible = true
        progress.snapTo(1f)
        dragOffset.snapTo(0f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = notification.durationMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                easing = LinearEasing
            )
        )
        requestDismiss(NotificationDismissMode.AUTO)
    }

    LaunchedEffect(dismissalRequested) {
        if (dismissalRequested && dismissMode == NotificationDismissMode.AUTO) {
            delay(ExitAnimationDurationMillis)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(animationSpec = tween(280)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = ExitAnimationDurationMillis.toInt()),
            targetOffsetY = { -it / 3 }
        ) + fadeOut(animationSpec = tween(durationMillis = ExitAnimationDurationMillis.toInt())) +
            scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(durationMillis = ExitAnimationDurationMillis.toInt())
            ) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = ExitAnimationDurationMillis.toInt())
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .widthIn(max = 560.dp)
                .padding(top = topPadding)
                .graphicsLayer {
                    translationY = dragOffset.value
                    alpha = (1f - ((-dragOffset.value) / (dismissThresholdPx * 1.7f)))
                        .coerceIn(0.45f, 1f)
                }
                .pointerInput(notification.id, dismissThresholdPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            val updatedOffset = (dragOffset.value + dragAmount).coerceAtMost(0f)
                            coroutineScope.launch {
                                dragOffset.snapTo(updatedOffset)
                            }
                        },
                        onDragEnd = {
                            if (-dragOffset.value >= dismissThresholdPx) {
                                requestDismiss(NotificationDismissMode.USER)
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            NotificationCard(
                notification = notification,
                progress = progress.value
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: ScreenNotification,
    progress: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NotificationShape,
        color = Color.Transparent,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, NotificationBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(NotificationCardGradient)
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = NotificationGlowGradient,
                            radius = 700f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NotificationAvatar()

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = notification.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NotificationTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = notification.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NotificationTextSecondary
                        )
                    }
                }

                NotificationProgressBar(progress = progress)
            }
        }
    }
}

@Composable
private fun NotificationAvatar() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(NotificationLogoBackground)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.24f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.safesteps_launchapp_foreground),
            contentDescription = "SafeSteps",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun NotificationProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(NotificationTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(brush = Brush.linearGradient(NotificationAccentGradient))
        )
    }
}
