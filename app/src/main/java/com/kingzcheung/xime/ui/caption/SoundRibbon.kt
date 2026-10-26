package com.kingzcheung.xime.ui.caption

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

// Cylenx menu ribbon (core/ui/pulse_geometry.gd, pulse_recipe): 19 hairlines over 96 points.
private const val LINES = 19
private const val POINTS = 96
private const val CYCLES = 1.65f
private const val AMPLITUDE = .36f
private const val ATTACK = 32f
// Falls back over about half a second, so a word stays visible instead of flicking once and vanishing.
private const val RELEASE = 7f
private const val QUIET = .001f
/** Room hum below this stays flat, so a quiet room costs no redraws. */
private const val FLOOR = .06f
/**
 * Loudness stays absolute (the spectrum's fixed dB window), so quiet, normal and loud speech look
 * different; a curve lifts the middle so normal speech uses most of the height. A linear scale on a
 * 24 dp strip left normal speech a sliver that flicked once and vanished (user 2026-10-27).
 */
private const val LOUD = .75f
private const val CURVE = .6f
internal fun ribbonLevel(band: Float): Float =
    if (band < FLOOR) 0f else ((band - FLOOR) / (LOUD - FLOOR)).coerceIn(0f, 1f).let { Math.pow(it.toDouble(), CURVE.toDouble()).toFloat() }

/**
 * Sound made visible for a reader who cannot hear it: a ribbon of fine lines that opens where
 * the voice has energy (low pitches left, high right) and lies flat in silence. Purely
 * decorative and touch-transparent; it never animates while the room is quiet.
 */
@Composable
internal fun SoundRibbon(bands: FloatArray, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val target by rememberUpdatedState(if (active) bands else null)
    val shown = remember { FloatArray(bands.size) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            // Sleep until there is sound to follow or a shape to let settle.
            if (shown.all { it == 0f }) {
                snapshotFlow { target?.any { it > FLOOR } == true }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60 else ((now - last) / 1e9f).coerceIn(0f, .1f)
                last = now
                val goal = target
                for (i in shown.indices) {
                    val want = ribbonLevel(goal?.getOrNull(i) ?: 0f)
                    val speed = if (want > shown[i]) ATTACK else RELEASE
                    shown[i] += (want - shown[i]) * (1f - exp(-dt * speed))
                    if (want == 0f && shown[i] < QUIET) shown[i] = 0f
                }
                frame = now
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val w = size.width; val h = size.height
        val stroke = 1.2.dp.toPx()
        val ys = Array(LINES) { FloatArray(POINTS + 1) }
        for (line in 0 until LINES) {
            val layer = line.toFloat() / (LINES - 1)
            for (p in 0..POINTS) {
                val t = p.toFloat() / POINTS
                val position = t * (shown.size - 1)
                val index = position.toInt().coerceAtMost(shown.size - 1)
                val next = (index + 1).coerceAtMost(shown.size - 1)
                val f = position - index
                val energy = shown[index] + (shown[next] - shown[index]) * f * f * (3 - 2 * f)
                val curvature = sin(t * 2 * PI.toFloat() * CYCLES + layer * 2.8f)
                ys[line][p] = h * (.5f + AMPLITUDE * energy * (curvature + (layer - .5f) * .7f))
            }
        }
        val peak = shown.maxOrNull() ?: 0f
        for (line in 0 until LINES) {
            val layer = line.toFloat() / (LINES - 1)
            val activeAlpha = .2f + .6f * (1f - abs(layer - .5f) * 2f) + peak * .12f
            for (p in 0 until POINTS) {
                val t = (p + .5f) / POINTS
                var top = ys[0][p]; var bottom = top
                for (l in 1 until LINES) { top = minOf(top, ys[l][p]); bottom = maxOf(bottom, ys[l][p]) }
                val spread = ((bottom - top) / 8f).coerceIn(0f, 1f)
                val activity = spread * spread * (3 - 2 * spread)
                val edge = smooth(t / .06f) * (1f - smooth((t - .94f) / .06f))
                val alpha = (.12f / LINES + (activeAlpha - .12f / LINES) * activity) * edge
                if (alpha <= .002f) continue
                drawLine(color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)),
                    Offset(w * p / POINTS, ys[line][p]), Offset(w * (p + 1) / POINTS, ys[line][p + 1]), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
