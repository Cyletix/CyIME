package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.kingzcheung.xime.settings.CustomKeyboardLayout

internal val LocalCustomLayout = staticCompositionLocalOf<CustomKeyboardLayout?> { null }
internal val LocalCustomAccent = staticCompositionLocalOf { Color(0xFF6750A4) }
internal fun layoutContrast(a: Color, b: Color): Float =
    (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
private fun readable(color: Color, background: Color): Color {
    val end = if (layoutContrast(Color.Black, background) > layoutContrast(Color.White, background)) Color.Black else Color.White
    return (0..20).map { lerp(color, end, it / 20f) }.firstOrNull { layoutContrast(it, background) >= 4.5f } ?: end
}
// Use the keyboard's own accent, adjusting lightness only to keep labels legible.
internal fun vowelColor(background: Color, accent: Color): Color = readable(accent, background)
internal fun customLayoutKeyColors(layout: CustomKeyboardLayout?, text: String, background: Color, foreground: Color, accent: Color): Pair<Color, Color> {
    val key = text.lowercase()
    if (layout == null || key !in layout.rows.flatten()) return background to foreground
    val changed = key.length > 1 || key.any { it in layout.movedLetters() }
    val bg = if (changed) lerp(background, accent, if (background.luminance() < .3f) .38f else .22f) else background
    val fg = if (key.all { layout.isRed(it) }) vowelColor(bg, accent) else readable(foreground, bg)
    return bg to fg
}
internal fun customLayoutLabel(layout: CustomKeyboardLayout?, text: String, background: Color, accent: Color): AnnotatedString = buildAnnotatedString {
    text.forEach { letter ->
        if (layout != null && text.lowercase() in layout.rows.flatten() && layout.isRed(letter))
            withStyle(SpanStyle(color = vowelColor(background, accent))) { append(letter) }
        else append(letter)
    }
}
