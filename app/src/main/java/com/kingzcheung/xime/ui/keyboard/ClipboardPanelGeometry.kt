package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute

internal fun clipboardPanelCanExpand(page: KeyboardPage, resizing: Boolean, compact: Boolean): Boolean =
    !resizing && !compact && (page as? KeyboardPage.Overlay)?.route is OverlayRoute.Clipboard

internal data class ClipboardPanelBounds(val collapsed: Int, val expanded: Int) {
    fun clamp(height: Float): Float = height.coerceIn(collapsed.toFloat(), expanded.toFloat())

    fun shouldExpand(height: Float): Boolean =
        expanded > collapsed && height > (collapsed + expanded) / 2f
}

/** Reserved space includes system bars, bottom padding and (when floating) the card's bottom offset. */
internal fun clipboardPanelBounds(normal: Int, screen: Int, reserved: Int): ClipboardPanelBounds {
    val available = (screen - reserved.coerceAtLeast(0)).coerceAtLeast(1)
    val collapsed = normal.coerceIn(1, available)
    // Target 80% of the screen, then clamp once for bars and the floating bottom offset.
    val target = minOf(available, screen * 80 / 100)
    return ClipboardPanelBounds(collapsed, maxOf(collapsed, target))
}

/** Use screen coordinates: resizing the IME moves the toolbar's local coordinate origin. */
internal class ClipboardPanelDrag(
    private val bounds: ClipboardPanelBounds,
    private val startHeight: Float,
    private val startScreenY: Float,
) {
    fun heightAt(screenY: Float): Float = bounds.clamp(startHeight + startScreenY - screenY)
}
