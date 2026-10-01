package com.kingzcheung.xime.handwriting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette

/** Preserve the theme accent, adjusting only as far toward a neutral as contrast requires. */
fun handwritingInk(preferred: Color, background: Color): Color {
    val surface = background.compositeOver(Color.White)
    fun contrast(ink: Color): Float {
        val a = ink.luminance(); val b = surface.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    val opaque = preferred.copy(alpha = 1f)
    if (contrast(opaque) >= 4.5f) return opaque
    val target = if (contrast(Color.Black) > contrast(Color.White)) Color.Black else Color.White
    if (opaque.red == opaque.green && opaque.green == opaque.blue) return target
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2
        if (contrast(lerp(opaque, target, middle)) >= 4.5f) high = middle else low = middle
    }
    return lerp(opaque, target, high)
}

/** Full-screen paper is transparent; its unknown host is protected by the stroke keylines. */
@Composable
internal fun rememberHandwritingInk(background: Color): Color {
    val palette = LocalKeyboardPalette.current
    val accent = palette?.accent ?: MaterialTheme.colorScheme.primary
    val surface = background.compositeOver(palette?.background ?: MaterialTheme.colorScheme.surface)
    return remember(accent, surface) { handwritingInk(accent, surface) }
}
