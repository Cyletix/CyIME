package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.HardwareCursorAnchor
import kotlin.math.roundToInt

internal data class HardwareCandidatePosition(val x: Int, val y: Int)

/** Host-local rectangle, kept independent of Android for positioning tests. */
internal data class HardwareCandidateExclusion(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Position a measured candidate card in the actual IME host, using screen-space cursor bounds. */
internal fun hardwareCandidatePosition(
    viewWidth: Int,
    viewHeight: Int,
    cardWidth: Int,
    cardHeight: Int,
    anchor: HardwareCursorAnchor?,
    hostScreenX: Int,
    hostScreenY: Int,
    marginPx: Int,
    gapPx: Int,
    avoidBounds: HardwareCandidateExclusion? = null,
): HardwareCandidatePosition {
    val width = viewWidth.coerceAtLeast(0)
    val height = viewHeight.coerceAtLeast(0)
    val measuredWidth = cardWidth.coerceAtLeast(0)
    val measuredHeight = cardHeight.coerceAtLeast(0)
    fun availableRange(extent: Int, cardExtent: Int): IntRange {
        val end = (extent - cardExtent).coerceAtLeast(0)
        // Small windows may not have room for both normal margins.
        val margin = marginPx.coerceAtLeast(0).coerceAtMost(end / 2)
        return margin..(end - margin)
    }
    val xRange = availableRange(width, measuredWidth)
    val yRange = availableRange(height, measuredHeight)
    fun avoidToolbar(preferred: HardwareCandidatePosition): HardwareCandidatePosition {
        val excluded = avoidBounds ?: return preferred
        if (excluded.left >= excluded.right || excluded.top >= excluded.bottom ||
            measuredWidth == 0 || measuredHeight == 0
        ) return preferred
        val gap = gapPx.coerceAtLeast(0).toLong()
        val left = excluded.left.toLong() - gap
        val top = excluded.top.toLong() - gap
        val right = excluded.right.toLong() + gap
        val bottom = excluded.bottom.toLong() + gap
        fun overlap(position: HardwareCandidatePosition): Long {
            val overlapWidth = (minOf(position.x.toLong() + measuredWidth, right) -
                maxOf(position.x.toLong(), left)).coerceAtLeast(0)
            val overlapHeight = (minOf(position.y.toLong() + measuredHeight, bottom) -
                maxOf(position.y.toLong(), top)).coerceAtLeast(0)
            return overlapWidth * overlapHeight
        }
        if (overlap(preferred) == 0L) return preferred
        fun clamped(x: Long, y: Long) = HardwareCandidatePosition(
            x.coerceIn(xRange.first.toLong(), xRange.last.toLong()).toInt(),
            y.coerceIn(yRange.first.toLong(), yRange.last.toLong()).toInt(),
        )
        // Keep the caret-aligned placement whenever possible. Otherwise move the shortest
        // distance to one side of the toolbar, preserving viewport bounds in tiny windows.
        return listOf(
            preferred,
            clamped(preferred.x.toLong(), top - measuredHeight),
            clamped(preferred.x.toLong(), bottom),
            clamped(left - measuredWidth, preferred.y.toLong()),
            clamped(right, preferred.y.toLong()),
        ).minWith(compareBy<HardwareCandidatePosition> { overlap(it) }.thenBy {
            val dx = it.x.toDouble() - preferred.x
            val dy = it.y.toDouble() - preferred.y
            dx * dx + dy * dy
        })
    }
    val fallback = HardwareCandidatePosition((width - measuredWidth).coerceAtLeast(0) / 2, yRange.last)
    if (anchor == null || !anchor.left.isFinite() || !anchor.top.isFinite() ||
        !anchor.right.isFinite() || !anchor.bottom.isFinite() ||
        anchor.left > anchor.right || anchor.top > anchor.bottom
    ) return avoidToolbar(fallback)

    val left = anchor.left.toDouble() - hostScreenX
    val right = anchor.right.toDouble() - hostScreenX
    val top = anchor.top.toDouble() - hostScreenY
    val bottom = anchor.bottom.toDouble() - hostScreenY
    // Zero and boundary coordinates are valid; only fully outside anchors are hidden.
    if (right < 0.0 || left > width || bottom < 0.0 || top > height) return avoidToolbar(fallback)

    val gap = gapPx.coerceAtLeast(0)
    val below = bottom + gap
    val preferredY = if (below <= yRange.last) below else top - measuredHeight - gap
    return avoidToolbar(HardwareCandidatePosition(
        left.coerceIn(xRange.first.toDouble(), xRange.last.toDouble()).roundToInt(),
        preferredY.coerceIn(yRange.first.toDouble(), yRange.last.toDouble()).roundToInt(),
    ))
}
