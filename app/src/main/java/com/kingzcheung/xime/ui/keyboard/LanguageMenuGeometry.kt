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
    val availableWidth = (panel.width - 2 * margin).coerceAtLeast(1f)
    val availableHeight = (panel.height - 2 * margin).coerceAtLeast(1f)
    val width = (panel.width * 0.6f).coerceIn(minOf(160f * d, availableWidth), minOf(280f * d, availableWidth))
    val rowHeight = (panel.height * 0.23f).coerceIn(44f * d, 64f * d)
    val height = minOf(40f * d + count.coerceAtLeast(1) * rowHeight, availableHeight)
    val x = (key.right - width).coerceIn(panel.left + margin, panel.right - margin - width)
    val y = (key.top - height).coerceIn(panel.top + margin, panel.bottom - margin - height)
    return LanguageMenuGeometry(Rect(x, y, x + width, y + height), rowHeight)
}
