package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kingzcheung.xime.glide.GlideTracker
import kotlin.math.ceil

internal val LocalT9GlideKeys = staticCompositionLocalOf<MutableMap<Char, Rect>?> { null }

/** Sample segments as well as events so the same path on a slower touchscreen gives the same digits. */
private class DigitStroke(private val keys: Map<Char, Rect>, start: Offset, first: Char) {
    private var previous = start
    private val step = (keys.values.minOf { it.minDimension } / 4f).coerceAtLeast(1f)
    val digits = StringBuilder().append(first)
    var cancelled = false
        private set

    fun move(end: Offset) {
        val count = ceil((end - previous).getDistance() / step).toInt().coerceAtLeast(1)
        for (i in 1..count) {
            val point = lerp(previous, end, i.toFloat() / count)
            val hit = keys.entries.firstOrNull { it.value.contains(point) }
            if (hit == null) {
                // Small gaps between keys are traversable. A visit to the rail or a function key cancels.
                if (keys.values.none { it.inflate(step / 2).contains(point) }) { cancelled = true; break }
            } else if (hit.key != digits.last() && hit.value.deflate(hit.value.minDimension * 0.18f).contains(point)) {
                digits.append(hit.key)
            }
        }
        previous = end
    }
}

@Composable
internal fun Modifier.t9GlideTyping(
    keys: Map<Char, Rect>?, trail: GlideTrail, rootOrigin: () -> Offset,
    admission: () -> Long?, onDigits: (String) -> Unit,
): Modifier {
    val origin = rememberUpdatedState(rootOrigin)
    val ticket = rememberUpdatedState(admission)
    val submit = rememberUpdatedState(onDigits)
    if (keys == null) return this
    return pointerInput(keys, trail) {
        awaitEachGesture {
            val down = awaitFirstDown(false, PointerEventPass.Initial)
            val offset = origin.value()
            val first = keys.entries.firstOrNull { it.value.contains(down.position + offset) } ?: return@awaitEachGesture
            val editor = ticket.value() ?: return@awaitEachGesture
            val stroke = DigitStroke(keys.toMap(), down.position + offset, first.key)
            val path = arrayListOf(down.position)
            var moved = false
            var claimed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    if (event.changes.any { it.id != down.id && it.pressed }) {
                        // Once the first button yielded to the glide, discard the whole cancelled
                        // stroke, including the second down. Before takeover, allow normal two-thumb taps.
                        if (claimed) {
                            event.changes.forEach { it.consume() }
                            while (true) {
                                val remaining = awaitPointerEvent(PointerEventPass.Initial)
                                remaining.changes.forEach { it.consume() }
                                if (remaining.changes.none { it.pressed }) break
                            }
                        }
                        break
                    }
                    if (!moved && change.uptimeMillis - down.uptimeMillis >= GlideTracker.HOLD_MS) break
                    moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    for (position in change.historical.map { it.position } + change.position) {
                        if (path.last() != position) {
                            path += position
                            stroke.move(position + offset)
                            if (stroke.cancelled) break
                        }
                    }
                    claimed = claimed || stroke.digits.length > 1
                    if (claimed) change.consume() // the original button drops its tap/flick on this same event
                    if (stroke.cancelled || origin.value() != offset || ticket.value() != editor) break
                    if (!change.pressed) {
                        if (claimed) submit.value(stroke.digits.toString())
                        break
                    }
                    if (claimed) trail.points = path.toList()
                }
            } finally { trail.points = emptyList() }
        }
    }
}
