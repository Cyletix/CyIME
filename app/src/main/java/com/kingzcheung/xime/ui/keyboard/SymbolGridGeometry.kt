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
