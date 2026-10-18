package com.kingzcheung.xime.ui.keyboard

internal data class SymbolGridGeometry(val columns: Int, val rowHeightDp: Float)

/** Size from this panel's viewport, not device orientation. Three visible rows share
 * its height; narrow floating panels shrink gently, long categories scroll. */
internal fun symbolGridGeometry(widthDp: Float, heightDp: Float): SymbolGridGeometry {
    val width = widthDp.coerceAtLeast(1f)
    val height = heightDp.coerceAtLeast(1f)
    val minimumRow = minOf(40f, width / 4f).coerceAtLeast(1f)
    val row = (height / 3f).coerceIn(minimumRow, 96f)
    val columns = (width / row).toInt().coerceIn(4, 12)
    return SymbolGridGeometry(columns, row)
}

/** Emoji fills the viewport with more cells, rather than inflating three rows.
 * Include inter-row gaps so a short panel still shows whole rows. */
internal fun emojiGridGeometry(widthDp: Float, heightDp: Float): SymbolGridGeometry {
    val width = widthDp.coerceAtLeast(1f)
    val height = heightDp.coerceAtLeast(1f)
    val rows = ((height + 2f) / 42f).toInt().coerceAtLeast(1)
    val rowHeight = ((height - 2f * (rows - 1)) / rows).coerceAtMost(52f)
    return SymbolGridGeometry((width / 44f).toInt().coerceAtLeast(1), rowHeight)
}
