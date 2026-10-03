package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.LayoutKind

/** Visual cap geometry, sharing the renderer's gutters and adaptive gaps. */
internal data class KeyboardSquareSnap(val columns: Float, val split: Boolean, val keypad: Boolean) {
    val section get() = if (keypad) "t9" else "qwerty"

    fun capDifference(rect: ResizeRect, density: Float, extra: Float,
        floating: Boolean, spacing: Pair<Float?, Float?>): Float {
        val width = rect.width / density
        val bodyHeight = (rect.height - extra) / density - 44f
        val fraction = if (split) 0.9f else 1f
        val policy = (if (keypad) KeyVisualPolicy.T9 else KeyVisualPolicy.Qwerty)
            .copy(maxKeyWidth = Float.MAX_VALUE)
        val metrics = keyVisualMetrics(policy, width * fraction, bodyHeight, columns,
            allowShrink = floating).withAppliedGutter(width, columns, 8f, true, fraction)
        fun gap(explicit: Float?, inset: Float?) = explicit
            ?.takeUnless { it == KeyVisualPolicy.DeclaredDefaultGap } ?: ((inset ?: 0f) * 2f)
        val cellWidth = metrics.cellWidthDp - if (split) 8f * fraction / columns else 0f
        return cellWidth - gap(spacing.first, metrics.insetX) -
            ((bodyHeight - 8f) / 4f - gap(spacing.second, metrics.insetY))
    }

    companion object {
        fun forLayout(kind: LayoutKind, split: Boolean): KeyboardSquareSnap? = when (kind) {
            LayoutKind.ALPHABETIC -> KeyboardSquareSnap(10f, split, false)
            LayoutKind.T9 -> KeyboardSquareSnap(5f * 3f / 3.4f, false, true)
            else -> null
        }
    }
}

/** Only snap the dragged edge, within six dp; leaving that band releases immediately. */
internal fun ResizeRect.snapSquareKeys(handle: ResizeHandle, geometry: KeyboardSquareSnap?,
    bounds: ResizeRect, density: Float, extra: Float, floating: Boolean,
    spacing: Pair<Float?, Float?>, thresholdDp: Float = 6f): ResizeRect {
    if (geometry == null || handle == ResizeHandle.NONE) return this
    // Reaching the window boundary wins over the optional square detent.
    // Otherwise a square point six dp short of the edge could prevent full width again.
    val atWidthBoundary = when (handle) {
        ResizeHandle.LEFT, ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> left <= bounds.left + 0.5f * density
        ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> right >= bounds.right - 0.5f * density
        else -> false
    }
    if (atWidthBoundary) return this
    val horizontal = handle in listOf(ResizeHandle.LEFT, ResizeHandle.RIGHT,
        ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT)
    fun shifted(delta: Float): ResizeRect = when (handle) {
        ResizeHandle.LEFT, ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> copy(left = left + delta)
        ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> copy(right = right + delta)
        ResizeHandle.TOP -> copy(top = top + delta)
        ResizeHandle.BOTTOM -> copy(bottom = bottom + delta)
        else -> this
    }
    val tolerance = thresholdDp * density
    var low = -tolerance
    var high = tolerance
    fun error(delta: Float) = geometry.capDifference(shifted(delta), density, extra, floating, spacing)
    val increasing = (horizontal && handle in listOf(ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT,
        ResizeHandle.BOTTOM_RIGHT)) || handle == ResizeHandle.TOP
    if (error(low) * error(high) > 0f) return this
    repeat(20) {
        val mid = (low + high) / 2f
        if ((error(mid) < 0f) == increasing) low = mid else high = mid
    }
    val snapped = shifted((low + high) / 2f)
    return if (snapped.left >= bounds.left && snapped.right <= bounds.right &&
        snapped.top >= bounds.top && snapped.bottom <= bounds.bottom &&
        snapped.width > 0f && snapped.height > 0f) snapped else this
}
