package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Pointy-top hexagon filling its box: the vertical sides span the middle half. */
internal object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        return Outline.Generic(Path().apply {
            moveTo(w / 2f, 0f); lineTo(w, h / 4f); lineTo(w, h * 3f / 4f)
            lineTo(w / 2f, h); lineTo(0f, h * 3f / 4f); lineTo(0f, h / 4f); close()
        })
    }
}

/** Shift and delete sit in the third row's half-key margins: each is the half of a hexagon centred on the edge. */
internal enum class HoneycombEdge { START, END }
internal val LocalHoneycombEdge = staticCompositionLocalOf<HoneycombEdge?> { null }

internal class HalfHexagonShape(private val edge: HoneycombEdge) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        // START keeps the right half (the cut runs along the keyboard's left edge); END mirrors it.
        val (cut, side) = if (edge == HoneycombEdge.START) 0f to w else w to 0f
        return Outline.Generic(Path().apply {
            moveTo(cut, 0f); lineTo(side, h / 4f); lineTo(side, h * 3f / 4f); lineTo(cut, h); close()
        })
    }
}

/**
 * Rows are plain cells (the touch area). The cap is a third taller and centred, so a row's points
 * reach into the notches of the half-offset rows above and below and the hexagons interlock.
 */
internal fun Modifier.honeycombCap(): Modifier = layout { measurable, constraints ->
    val height = constraints.maxHeight
    if (height == Constraints.Infinity) return@layout measurable.measure(constraints).let { layout(it.width, it.height) { it.place(0, 0) } }
    val tall = (height * 4f / 3f).roundToInt()
    val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
    layout(placeable.width, height) { placeable.place(0, (height - tall) / 2) }
}

/** Labels stay inside the hexagon: off the slanted top and bottom, and in from the narrowing corners.
 * A half hexagon is full width only in its middle half, so its icon and hint keep to that band. */
internal fun Modifier.honeycombLabelArea(half: Boolean = false): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight || !constraints.hasBoundedWidth)
        return@layout measurable.measure(constraints).let { layout(it.width, it.height) { it.place(0, 0) } }
    val dx = if (half) 0 else constraints.maxWidth / 8
    val dy = constraints.maxHeight / (if (half) 4 else 8)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth - 2 * dx, constraints.maxHeight - 2 * dy))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(dx, dy) }
}
