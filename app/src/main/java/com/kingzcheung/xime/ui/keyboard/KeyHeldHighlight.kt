package com.kingzcheung.xime.ui.keyboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette

/** A stationary tint survives the short press animation without an animation clock. */
@Composable
internal fun Modifier.keyHeldHighlight(held: Boolean): Modifier {
    val accent = LocalKeyboardPalette.current?.accent ?: MaterialTheme.colorScheme.primary
    val radius = LocalKeyCornerRadius.current
    if (!held) return this
    return drawBehind {
        drawRoundRect(accent.copy(alpha = 0.18f), cornerRadius = CornerRadius(radius.toPx()))
        val stroke = 1.dp.toPx()
        drawRoundRect(accent.copy(alpha = 0.8f),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((radius.toPx() - stroke / 2f).coerceAtLeast(0f)),
            style = Stroke(stroke))
    }
}
