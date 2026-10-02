package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.LayoutKind
import kotlin.math.abs

/** Preferred default proportions for the key body, excluding toolbar and outer padding.
 * Manual sizes use a much wider safety envelope; these are not drag stops. */
internal data class KeyboardAspectLimits(val min: Float, val max: Float) {
    fun minWidth(height: Float) = 16f + (height - 60f).coerceAtLeast(1f) * min
    fun maxWidth(height: Float) = 16f + (height - 60f).coerceAtLeast(1f) * max
    fun maxHeight(width: Float) = 60f + (width - 16f).coerceAtLeast(1f) / min
    val extremes: KeyboardAspectLimits get() = KeyboardAspectLimits(min * 0.45f, max * 2f)

    companion object {
        // Ten columns / four rows: 0.55:1 to near-square; leave room for unequal visual gaps.
        val Letters = KeyboardAspectLimits(1.375f, 2.4f)
        // Keypads have fewer, deliberately wider buttons, including side actions.
        val Keypad = KeyboardAspectLimits(0.9f, 2f)
        fun forLayout(kind: LayoutKind) = when (kind) {
            LayoutKind.T9, LayoutKind.KANA_KEYPAD, LayoutKind.STROKE_KEYPAD,
            LayoutKind.NUMBER, LayoutKind.HANDWRITING -> Keypad
            LayoutKind.ALPHABETIC, LayoutKind.MERGED -> Letters
        }
    }
}

internal data class ProtectedKeyboardSize(val width: Int, val height: Int, val offsetX: Int)

/** Default sizes use preferred proportions; explicit sizes only use extreme safeguards.
 * Widen a narrow keyboard first; only lower it if the available window cannot fit it. */
internal fun protectKeyboardSize(
    availableWidth: Int, requestedWidth: Int, requestedHeight: Int, offsetX: Int,
    limits: KeyboardAspectLimits,
    customSize: Boolean = false,
    letterDefaults: LetterKeyboardDefaults? = null,
): ProtectedKeyboardSize {
    if (!customSize && letterDefaults != null) {
        return defaultLetterKeyboardSize(availableWidth, requestedWidth, letterDefaults)
    }
    val bounds = if (customSize) limits.extremes else limits
    val available = availableWidth.coerceAtLeast(1)
    val height = requestedHeight.coerceAtLeast(1)
        .coerceAtMost(bounds.maxHeight(available.toFloat()).toInt().coerceAtLeast(1))
    val minWidth = kotlin.math.ceil(bounds.minWidth(height.toFloat())).toInt().coerceIn(1, available)
    val maxWidth = bounds.maxWidth(height.toFloat()).toInt().coerceIn(minWidth, available)
    val width = requestedWidth.coerceAtLeast(1).coerceIn(minWidth, maxWidth)
    val repaired = abs(width - requestedWidth) > 1 || abs(height - requestedHeight) > 1
    val travel = (available - width) / 2
    return ProtectedKeyboardSize(width, height,
        if (repaired) 0 else offsetX.coerceIn(-travel, travel))
}

/** Test the key body in pixels; extraHeight contains bottom padding or the floating drag bar. */
internal fun ResizeRect.hasKeyboardAspect(limits: KeyboardAspectLimits, density: Float, extraHeight: Float): Boolean {
    val h = (height - extraHeight) / density
    val w = width / density
    return w >= limits.minWidth(h) - 1f && w <= limits.maxWidth(h) + 1f
}

/** Progressive resistance outside the preferred shape; moving back is unrestricted.
 * The curve starts flat, so small nearby drags retain the original feel. Opposite edges stay put. */
internal fun ResizeRect.resistKeyboardAspectDrag(
    proposed: ResizeRect, limits: KeyboardAspectLimits, density: Float, extraHeight: Float,
): ResizeRect {
    fun distance(rect: ResizeRect): Float {
        val bodyWidth = (rect.width / density - 16f).coerceAtLeast(1f)
        val bodyHeight = ((rect.height - extraHeight) / density - 60f).coerceAtLeast(1f)
        val ratio = bodyWidth / bodyHeight
        return when {
            ratio > limits.max -> ratio / limits.max - 1f
            ratio < limits.min -> limits.min / ratio - 1f
            else -> 0f
        }
    }
    fun response(departure: Float): Float {
        val squared = departure * departure
        // Flat at the preferred boundary; the distant tail gets progressively stronger.
        return 1f / (1f + 4f * squared + 24f * squared * squared)
    }
    val dl = proposed.left - left
    val dt = proposed.top - top
    val dr = proposed.right - right
    val db = proposed.bottom - bottom
    fun ResizeRect.advance(t: Float) = ResizeRect(left + dl * t, top + dt * t,
        right + dr * t, bottom + db * t)
    // Integrate the actual resisted path, not the unresisted event endpoint. Large/coalesced
    // touch events must feel like small ones, without suddenly skipping the gentle onset.
    val steps = kotlin.math.ceil(maxOf(abs(dl), abs(dt), abs(dr), abs(db)) / (2f * density))
        .toInt().coerceAtLeast(1)
    val step = 1f / steps
    var resisted = this
    repeat(steps) {
        val before = distance(resisted)
        val after = distance(resisted.advance(step))
        val factor = if (after <= before + 0.0001f) 1f else {
            val midpoint = resisted.advance(step * response(before) * 0.5f)
            response(distance(midpoint))
        }
        resisted = resisted.advance(step * factor)
    }
    val extremes = limits.extremes
    if (resisted.hasKeyboardAspect(extremes, density, extraHeight)) return resisted
    if (!hasKeyboardAspect(extremes, density, extraHeight)) return this
    fun at(t: Float) = ResizeRect(left + (resisted.left - left) * t,
        top + (resisted.top - top) * t, right + (resisted.right - right) * t,
        bottom + (resisted.bottom - bottom) * t)
    var low = 0f
    var high = 1f
    repeat(18) {
        val mid = (low + high) / 2f
        if (at(mid).hasKeyboardAspect(extremes, density, extraHeight)) low = mid else high = mid
    }
    return at(low)
}
