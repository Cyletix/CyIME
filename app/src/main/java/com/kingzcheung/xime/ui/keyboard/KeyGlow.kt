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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class GlowParticle(val x: Float, val y: Float, val side: Float, val angle: Float, val spin: Float)

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
    val elapsed = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    var particles by remember { mutableStateOf(emptyList<GlowParticle>()) }
    this.pointerInput(Unit) {
        awaitEachGesture {
            // 只旁观 DOWN，因此同一帧内按下/松开也不会丢失动画；不干扰拂动和长按。
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            animation?.cancel()
            particles = List(4) {
                GlowParticle(Random.nextFloat(), Random.nextFloat(), 0.65f + Random.nextFloat() * 0.50f,
                    Random.nextFloat() * 120f - 60f, Random.nextFloat() * 70f - 35f)
            }
            animation = scope.launch {
                elapsed.snapTo(0f)
                // This is only the clock. Scale, travel and opacity each have their own
                // continuous nonlinear curve below; none waits at an intermediate state.
                elapsed.animateTo(1f, tween(500, easing = LinearEasing))
            }
        }
    }.graphicsLayer {
        val scale = keyGlowScale(elapsed.value)
        scaleX = scale
        scaleY = scale
    }.then(cap).drawWithCache {
        // Cached feathered brushes, no per-frame blur bitmap or offscreen layer.
        val glowBrush = Brush.radialGradient(
            listOf(colors[0].copy(alpha = 0.65f), colors[1].copy(alpha = 0.22f), Color.Transparent),
            center = Offset.Zero, radius = size.maxDimension.coerceAtLeast(1f) * 0.8f,
        )
        val particleBrushes = colors.map { color ->
            Brush.radialGradient(
                0f to color.copy(alpha = 0.85f),
                0.18f to color.copy(alpha = 0.75f),
                0.45f to color.copy(alpha = 0.38f),
                0.72f to color.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = Offset.Zero, radius = 1f,
            )
        }
        onDrawWithContent {
            val t = elapsed.value
            if (t < 1f && particles.isNotEmpty()) {
                val remaining = keyGlowRemaining(t)
                val fade = (t / 0.02f).coerceIn(0f, 1f) * remaining
                val travel = keyGlowTravel(t)
                val glowCenter = Offset(size.width * 0.5f, size.height * 0.55f)
                translate(glowCenter.x, glowCenter.y) {
                    drawRect(glowBrush, topLeft = -glowCenter, size = size, alpha = fade,
                        blendMode = BlendMode.Screen)
                }
                particles.forEachIndexed { i, particle ->
                    val radius = size.minDimension * particle.side * (0.25f + 0.30f * remaining)
                    val center = Offset(
                        size.width * (0.5f + (particle.x - 0.5f) * travel),
                        size.height * (0.55f + (particle.y - 0.55f) * travel),
                    )
                    translate(center.x, center.y) {
                        rotate(particle.angle + particle.spin * travel, Offset.Zero) {
                            scale(radius, radius * 0.72f, Offset.Zero) {
                                drawCircle(particleBrushes[i % particleBrushes.size], radius = 1f,
                                    center = Offset.Zero, alpha = fade, blendMode = BlendMode.Screen)
                            }
                        }
                    }
                }
            }
            drawContent() // Keep glyphs sharp, above the light.
        }
    }
}

/** One smooth pulse over 150 ms, peaking at 37.5 ms; no held/shrunken plateau. */
internal fun keyGlowScale(t: Float): Float {
    val x = (t / 0.3f).coerceIn(0f, 1f)
    val tail = 1f - x
    val pulse = (256f / 27f) * x * tail * tail * tail
    return 1f - 0.12f * pulse
}

/** Opacity has an independent smooth tail across the complete 500 ms. */
internal fun keyGlowRemaining(t: Float): Float {
    val remaining = 1f - t.coerceIn(0f, 1f)
    return remaining * remaining
}

/** Rotation and outward travel start fast and decelerate continuously. */
internal fun keyGlowTravel(t: Float): Float {
    val remaining = 1f - t.coerceIn(0f, 1f)
    return 1f - remaining * remaining * remaining * remaining
}
