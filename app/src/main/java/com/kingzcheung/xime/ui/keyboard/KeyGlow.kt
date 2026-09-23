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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class GlowSquare(val x: Float, val y: Float, val side: Float, val angle: Float, val spin: Float)

/** 放在键帽 clip/background 之后：只绘制键内装饰，不消费事件，不创建悬浮窗口。 */
internal fun Modifier.keyGlow(): Modifier = composed {
    if (!LocalKeyboardInputPreferences.current.keyGlowEnabled) return@composed this
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
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
                GlowSquare(Random.nextFloat(), Random.nextFloat(), 0.20f + Random.nextFloat() * 0.23f,
                    Random.nextFloat() * 120f - 60f, Random.nextFloat() * 70f - 35f)
            }
            animation = scope.launch {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(480, easing = LinearEasing))
            }
        }
    }.drawWithContent {
        val t = progress.value
        if (t < 1f && squares.isNotEmpty()) {
            val fade = if (t < 0.12f) t / 0.12f else (1f - t) / 0.88f
            val glowCenter = Offset(size.width * (0.25f + t * 0.5f), size.height * 0.55f)
            drawRect(Brush.radialGradient(
                listOf(colors[0].copy(alpha = fade * 0.48f), colors[1].copy(alpha = fade * 0.18f), Color.Transparent),
                center = glowCenter, radius = size.maxDimension.coerceAtLeast(1f) * 0.85f))
            squares.forEachIndexed { i, square ->
                val side = size.minDimension * square.side * (0.6f + 0.4f * t)
                val center = Offset(size.width * square.x, size.height * square.y - size.height * t * 0.12f)
                rotate(square.angle + square.spin * t, center) {
                    drawRoundRect(colors[i % colors.size].copy(alpha = fade * 0.42f),
                        topLeft = center - Offset(side / 2, side / 2), size = Size(side, side),
                        cornerRadius = CornerRadius(side * 0.25f))
                }
            }
        }
        drawContent() // 字形始终位于装饰上方。
    }
}
