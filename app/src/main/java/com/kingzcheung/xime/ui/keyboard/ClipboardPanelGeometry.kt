package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute

internal fun clipboardPanelCanExpand(page: KeyboardPage, resizing: Boolean, compact: Boolean): Boolean =
    !resizing && !compact && (page as? KeyboardPage.Overlay)?.route is OverlayRoute.Clipboard

internal data class ClipboardPanelBounds(val collapsed: Int, val expanded: Int) {
    fun clamp(height: Float): Float = height.coerceIn(collapsed.toFloat(), expanded.toFloat())

    fun shouldExpand(height: Float, startHeight: Float = collapsed.toFloat(),
        wasExpanded: Boolean = false, velocityY: Float = 0f): Boolean {
        if (expanded <= collapsed) return false
        val distance = (expanded - collapsed) * .2f
        // Project through half of the shared 240ms opening transition. A quick flick
        // can settle without a long drag; slow sub-threshold movement returns home.
        val projectedTravel = height - startHeight - (velocityY.takeIf { it.isFinite() } ?: 0f) * .12f
        return when {
            projectedTravel >= distance -> true
            projectedTravel <= -distance -> false
            else -> wasExpanded
        }
    }
}

/** Reserved space includes system bars, bottom padding and (when floating) the card's bottom offset. */
internal fun clipboardPanelBounds(normal: Int, screen: Int, reserved: Int): ClipboardPanelBounds {
    val available = (screen - reserved.coerceAtLeast(0)).coerceAtLeast(1)
    val collapsed = normal.coerceIn(1, available)
    // Target 80% of the screen, then clamp once for bars and the floating bottom offset.
    val target = minOf(available, screen * 80 / 100)
    return ClipboardPanelBounds(collapsed, maxOf(collapsed, target))
}

/**
 * Clipboard search results take the space the clipboard has when pulled up, above the unchanged
 * keyboard and the search panel; 0 while search is closed. At least [CLIPBOARD_SEARCH_MIN_RESULTS]
 * when the screen has it, and never more than [available] (the height the IME may use: screen less
 * bars and bottom space), so in landscape the window never grows past the screen.
 */
internal fun clipboardSearchResultsTarget(visible: Boolean, bounds: ClipboardPanelBounds, keyboardHeight: Int,
    available: Int = bounds.expanded): Int {
    if (!visible) return 0
    val room = (available - keyboardHeight - CLIPBOARD_SEARCH_PANEL_HEIGHT).coerceAtLeast(0)
    return (bounds.expanded - keyboardHeight - CLIPBOARD_SEARCH_PANEL_HEIGHT)
        .coerceAtLeast(minOf(CLIPBOARD_SEARCH_MIN_RESULTS, room)).coerceAtMost(room)
}

internal const val CLIPBOARD_SEARCH_MIN_RESULTS = 120

/**
 * Height the IME container must add above the keyboard while clipboard search is open: the
 * (animated) results plus the search panel. Without it the keyboard is pushed out of the window.
 */
internal fun clipboardSearchExtraHeight(visible: Boolean, overlayPage: Boolean, resultsHeight: Int): Int =
    if (!visible || overlayPage) 0 else resultsHeight + CLIPBOARD_SEARCH_PANEL_HEIGHT

/** Use screen coordinates: resizing the IME moves the toolbar's local coordinate origin. */
internal class ClipboardPanelDrag(
    private val bounds: ClipboardPanelBounds,
    val startHeight: Float,
    private val startScreenY: Float,
) {
    fun heightAt(screenY: Float): Float = bounds.clamp(startHeight + startScreenY - screenY)
}
