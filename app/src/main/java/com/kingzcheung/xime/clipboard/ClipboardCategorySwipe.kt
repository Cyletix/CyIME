package com.kingzcheung.xime.clipboard

import kotlin.math.abs

/** Lock the initial intent; diagonal movement belongs to the vertical list. */
internal class ClipboardCategorySwipe(private val touchSlop: Float, private val pageDistance: Float) {
    private enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL }
    private var axis = Axis.UNDECIDED
    private var x = 0f
    private var y = 0f
    val horizontal: Boolean get() = axis == Axis.HORIZONTAL
    val vertical: Boolean get() = axis == Axis.VERTICAL

    fun move(totalX: Float, totalY: Float) {
        x = totalX
        y = totalY
        if (axis == Axis.UNDECIDED && x * x + y * y >= touchSlop * touchSlop) {
            axis = if (withinHorizontalCone()) Axis.HORIZONTAL else Axis.VERTICAL
        }
    }

    // A longer distance than scrolling is required, and one release changes at most one tab.
    fun pageDelta(): Int = when {
        !horizontal || !withinHorizontalCone() || abs(x) < pageDistance -> 0
        x < 0 -> 1
        else -> -1
    }

    private fun withinHorizontalCone() = abs(x) > 0 && abs(y) <= abs(x) * 0.5773503f
}

internal fun ClipboardFilter.afterSwipe(delta: Int): ClipboardFilter =
    ClipboardFilter.entries[(ordinal + delta).coerceIn(0, ClipboardFilter.entries.lastIndex)]
