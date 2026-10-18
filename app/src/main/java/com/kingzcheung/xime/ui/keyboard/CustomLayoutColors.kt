package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
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
// Derive a companion hue from the theme when its primary is already the normal label color.
internal fun vowelColor(background: Color, accent: Color, foreground: Color): Color {
    val primary = readable(accent, background)
    val distance = kotlin.math.abs(primary.red - foreground.red) +
        kotlin.math.abs(primary.green - foreground.green) + kotlin.math.abs(primary.blue - foreground.blue)
    if (distance >= .45f) return primary
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL((accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt(), hsl)
    hsl[0] = (hsl[0] + 150f) % 360f
    hsl[1] = hsl[1].coerceIn(.45f, .65f)
    hsl[2] = if (background.luminance() < .3f) .72f else .35f
    return readable(Color.hsl(hsl[0], hsl[1], hsl[2]), background)
}
/**
 * 玻璃键面的代表色：玻璃按主题文字方向把键帽统一提亮（浅字）或压暗（深字），
 * 实际键面与这里的底色无关，文字对比改按这个代表色计算。
 */
internal fun customLayoutGlassSurface(foreground: Color): Color =
    if (foreground.luminance() > .5f) Color(0xFF2C2C30) else Color(0xFFF4F4F6)

/** [labelSurface]：文字实际所在的表面（玻璃下传 [customLayoutGlassSurface]），默认就是算出的按键底色。 */
internal fun customLayoutKeyColors(layout: CustomKeyboardLayout?, text: String, background: Color, foreground: Color, accent: Color,
    labelSurface: Color? = null): Pair<Color, Color> {
    val key = text.lowercase()
    if (layout == null || key !in layout.rows.flatten()) return background to foreground
    val emphasized = key.any { layout.isRed(it) }
    val bg = if (emphasized) lerp(background, accent, if (background.luminance() < .3f) .38f else .22f) else background
    val surface = labelSurface ?: bg
    val fg = if (key.all { layout.isRed(it) }) vowelColor(surface, accent, foreground) else readable(foreground, surface)
    return bg to fg
}
internal fun customLayoutLabel(layout: CustomKeyboardLayout?, text: String, background: Color, accent: Color, foreground: Color): AnnotatedString = buildAnnotatedString {
    text.forEach { letter ->
        if (layout != null && text.lowercase() in layout.rows.flatten() && layout.isRed(letter))
            withStyle(SpanStyle(color = vowelColor(background, accent, foreground))) { append(letter) }
        else append(letter)
    }
}
