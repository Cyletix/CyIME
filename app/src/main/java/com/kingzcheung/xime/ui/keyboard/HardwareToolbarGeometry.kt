package com.kingzcheung.xime.ui.keyboard

/** Fractions of the currently available travel, retained across editors and rotations. */
internal data class HardwareToolbarPosition(val xFraction: Float = 0.5f, val yFraction: Float = 1f)

internal data class HardwareToolbarOffset(val x: Float, val y: Float)

internal enum class HardwareToolbarMode { FULL, COMPACT, KEYBOARD_ONLY }

internal fun hardwareToolbarMode(availableWidthDp: Float): HardwareToolbarMode = when {
    availableWidthDp >= 232f -> HardwareToolbarMode.FULL
    availableWidthDp >= 184f -> HardwareToolbarMode.COMPACT
    else -> HardwareToolbarMode.KEYBOARD_ONLY
}

internal data class HardwareToolbarGeometry(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    fun offset(position: HardwareToolbarPosition): HardwareToolbarOffset = HardwareToolbarOffset(
        minX + (maxX - minX) * fraction(position.xFraction, 0.5f),
        minY + (maxY - minY) * fraction(position.yFraction, 1f),
    )

    fun constrain(x: Float, y: Float): HardwareToolbarOffset = HardwareToolbarOffset(
        if (x.isFinite()) x.coerceIn(minX, maxX) else minX,
        if (y.isFinite()) y.coerceIn(minY, maxY) else maxY,
    )

    fun positionAt(
        x: Float,
        y: Float,
        previous: HardwareToolbarPosition,
        snapX: Boolean = true,
    ): HardwareToolbarPosition {
        val bounded = constrain(x, y)
        val xFraction = if (maxX > minX) (bounded.x - minX) / (maxX - minX)
            else fraction(previous.xFraction, 0.5f)
        val yFraction = if (maxY > minY) (bounded.y - minY) / (maxY - minY)
            else fraction(previous.yFraction, 1f)
        return HardwareToolbarPosition(
            if (snapX) when {
                xFraction < 0.25f -> 0f
                xFraction > 0.75f -> 1f
                else -> 0.5f
            } else xFraction,
            yFraction,
        )
    }

    private fun fraction(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback
}

internal fun hardwareToolbarGeometry(
    viewWidth: Int,
    viewHeight: Int,
    toolbarWidth: Int,
    toolbarHeight: Int,
    marginPx: Int,
): HardwareToolbarGeometry {
    fun range(extent: Int, toolbarExtent: Int): Pair<Float, Float> {
        val travel = (extent.coerceAtLeast(0) - toolbarExtent.coerceAtLeast(0)).coerceAtLeast(0)
        val margin = marginPx.coerceAtLeast(0).coerceAtMost(travel / 2)
        return margin.toFloat() to (travel - margin).toFloat()
    }
    val (minX, maxX) = range(viewWidth, toolbarWidth)
    val (minY, maxY) = range(viewHeight, toolbarHeight)
    return HardwareToolbarGeometry(minX, maxX, minY, maxY)
}
