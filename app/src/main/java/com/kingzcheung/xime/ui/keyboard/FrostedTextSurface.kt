package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import com.kingzcheung.xime.settings.FrostedGlassConfig
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Text treatment only: the rail inherits the exact same glass background as the keyboard. */
internal data class FrostedTextSurface(

    val text: Color,
    val selectedText: Color,
    val shadow: Shadow,
    val controlBackground: Color,
    /** 宿主换成反差色时托住字的光晕（见 [glassTextHalo]）；白、黑宿主上都已清楚时为 null。 */
    val halo: GlassTextHalo? = null,
)

internal val LocalFrostedTextSurface = staticCompositionLocalOf<FrostedTextSurface?> { null }

internal fun CandidateBarVisuals.withTextSurface(surface: FrostedTextSurface?): CandidateBarVisuals =
    if (surface == null) this else copy(
        textColor = surface.text,
        selectedTextColor = surface.selectedText,
        opaqueComments = true,
        textShadow = surface.shadow,
        glassControlBackground = surface.controlBackground,
    )

/**
 * Preserve readable theme colors and use only a small opposing glyph shadow for live content.
 * No extra rail tint is painted. When the rail's glass is too thin for a white or black host,
 * an opposing glyph halo of just the needed strength keeps the text at the keycap label contrast.
 * Existing round toolbar controls use the same key opacity as ordinary keycaps.
 */
internal fun resolveFrostedTextSurface(
    background: Color,
    text: Color,
    selectedText: Color = text,
    accent: Color = text,
    config: FrostedGlassConfig = FrostedGlassConfig(enabled = true),
    keyBackground: Color = background,
    shadowRadiusPx: Float = 2f,
): FrostedTextSurface {
    val surface = background.copy(alpha = 1f)
    val highlight = accent.copy(alpha = 0.2f)
    val normalText = readableThemeText(text, surface)
    val activeText = readableThemeText(selectedText, highlight.compositeOver(surface))
    return FrostedTextSurface(normalText, activeText,
        Shadow(color = glassHaloColor(normalText).copy(alpha = 0.8f), blurRadius = shadowRadiusPx),
        frostedKeyColor(keyBackground, normalText, config),
        glassTextHalo(listOf(normalText, activeText), surface, config.normalized().backgroundOpacity))
}
private fun readableThemeText(color: Color, background: Color): Color {
    val opaque = color.copy(alpha = 1f)
    val endpoint = if (textContrast(Color.Black, background) >= textContrast(Color.White, background))
        Color.Black else Color.White
    // Already-readable theme colors are untouched; adjust only colors that cannot work
    // on their own nominal theme surface.
    if (textContrast(opaque, background) >= 4.5f) return opaque
    val target = minOf(4.8f, textContrast(endpoint, background))
    var low = 0
    var high = 255
    while (low < high) {
        val middle = (low + high) / 2
        if (textContrast(lerp(opaque, endpoint, middle / 255f), background) >= target)
            high = middle else low = middle + 1
    }
    return lerp(opaque, endpoint, low / 255f)
}

internal fun textContrast(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}
