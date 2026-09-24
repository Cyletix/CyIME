package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class GlowSquare(val x: Float, val y: Float, val side: Float, val angle: Float, val spin: Float)

/** Transform only the cap/shadow, inside the unchanged key hit target. */
internal fun Modifier.keyGlow(cap: Modifier): Modifier = composed {
    if (!LocalKeyboardInputPreferences.current.keyGlowEnabled) return@composed this.then(cap)
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme.primary, scheme.tertiary, scheme.secondary) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary).map { color ->
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            hsv[1] = hsv[1].coerceAtLeast(0.75f)
            hsv[2] = hsv[2].coerceAtLeast(0.95f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    var squares by remember { mutableStateOf(emptyList<GlowSquare>()) }
    this.pointerInput(Unit) {
        awaitEachGesture {
            // 只旁观 DOWN，因此同一帧内按下/松开也不会丢失动画；不干扰拂动和长按。
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            animation?.cancel()
            squares = List(4) {
                GlowSquare(Random.nextFloat(), Random.nextFloat(), 0.65f + Random.nextFloat() * 0.50f,
                    Random.nextFloat() * 120f - 60f, Random.nextFloat() * 70f - 35f)
            }
            animation = scope.launch {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(500, easing = LinearEasing))
            }
        }
    }.graphicsLayer {
        val scale = keyGlowScale(progress.value)
        scaleX = scale
        scaleY = scale
    }.then(cap).drawWithCache {
        // 颜色和大小不变时复用渐变着色器；每帧只更新位移/透明度。
        val glowBrush = Brush.radialGradient(
            listOf(colors[0].copy(alpha = 0.72f), colors[1].copy(alpha = 0.32f), Color.Transparent),
            center = Offset.Zero, radius = size.maxDimension.coerceAtLeast(1f) * 0.85f,
        )
        onDrawWithContent {
            val t = progress.value
            if (t < 1f && squares.isNotEmpty()) {
                val remaining = keyGlowRemaining(t)
                val fade = (t / 0.02f).coerceAtMost(1f) * remaining
                val travel = 1f - remaining
                val glowCenter = Offset(size.width * 0.5f, size.height * 0.55f)
                translate(glowCenter.x, glowCenter.y) {
                    drawRect(glowBrush, topLeft = -glowCenter, size = size, alpha = fade)
                }
                squares.forEachIndexed { i, square ->
                    val side = size.minDimension * square.side * (0.06f + 0.94f * remaining)
                    val center = Offset(size.width * square.x, size.height * square.y - size.height * travel * 0.06f)
                    rotate(square.angle + square.spin * travel, center) {
                        drawRoundRect(colors[i % colors.size].copy(alpha = fade * 0.65f),
                            topLeft = center - Offset(side / 2, side / 2), size = Size(side, side),
                            cornerRadius = CornerRadius(side * 0.25f))
                    }
                }
            }
            drawContent() // 字形始终位于装饰上方。
        }
    }
}

/** Normalized time over 500 ms: shrink 50 ms, hold 50 ms, recover 50 ms. */
internal fun keyGlowScale(t: Float): Float = when {
    t < 0.1f -> 1f - 0.12f * (t / 0.1f).coerceAtLeast(0f)
    t < 0.2f -> 0.88f
    t < 0.3f -> 0.88f + 0.12f * ((t - 0.2f) / 0.1f)
    else -> 1f
}

/** Hold the large halo to 100 ms, then ease out quickly with a slow, faint tail. */
internal fun keyGlowRemaining(t: Float): Float {
    val remaining = 1f - ((t - 0.2f) / 0.8f).coerceIn(0f, 1f)
    return remaining * remaining * remaining
}
