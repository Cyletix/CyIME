package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal val LocalLanguageMenuPanel = staticCompositionLocalOf { Rect.Zero }

@Composable
internal fun LanguageMenuPanel(content: @Composable () -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalLanguageMenuPanel provides bounds, content = content)
    }
}

internal data class LanguageMenuGeometry(val bounds: Rect, val rowHeightPx: Float)

internal fun languageMenuGeometry(panel: Rect, key: Rect, density: Float, count: Int): LanguageMenuGeometry {
    val d = density.coerceAtLeast(0.1f)
    if (panel.width <= 0f || panel.height <= 0f) return LanguageMenuGeometry(Rect.Zero, 44f * d)
    val margin = minOf(8f * d, panel.width / 4f, panel.height / 4f)
    val area = panel.deflate(margin)
    val width = (panel.width * 0.6f).coerceIn(minOf(160f * d, area.width), minOf(280f * d, area.width))
    val rowHeight = (panel.height * 0.23f).coerceIn(44f * d, 64f * d)
    val height = minOf(40f * d + count.coerceAtLeast(1) * rowHeight, area.height)
    // Fit within the remaining space before translating to window coordinates. A full axis has zero travel.
    val x = area.left + (key.right - area.left - width).coerceIn(0f, area.width - width)
    val y = area.top + (key.top - area.top - height).coerceIn(0f, area.height - height)
    return LanguageMenuGeometry(Rect(x, y, minOf(x + width, area.right), minOf(y + height, area.bottom)), rowHeight)
}
