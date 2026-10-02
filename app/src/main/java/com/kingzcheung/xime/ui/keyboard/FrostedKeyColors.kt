package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kingzcheung.xime.settings.FrostedGlassConfig

/** Only the keycap fill is translucent; labels and icons keep their theme colors. */
internal fun frostedKeyColor(
    background: Color,
    foreground: Color,
    config: FrostedGlassConfig,
    legacyStateColor: Color = background,
    pressed: Boolean = false,
    highlighted: Boolean = false,
    opacityScale: Float = 1f,
): Color {
    if (!config.enabled) return legacyStateColor

    val opacity = config.keyOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.22f
    // A dark neutral fill would disappear over the dark panel at low opacity.
    // Taper the white tint as opacity rises so opaque keycaps retain readable contrast.
    // Colored action keys retain their theme hue.
    val chroma = maxOf(background.red, background.green, background.blue) -
        minOf(background.red, background.green, background.blue)
    val backgroundBrightness = (background.red + background.green + background.blue) / 3f
    val foregroundBrightness = (foreground.red + foreground.green + foreground.blue) / 3f
    val tint = if (chroma < 0.08f && backgroundBrightness < 0.5f && foregroundBrightness > 0.65f) {
        lerp(background, Color.White, 0.72f * (1f - opacity))
    } else background
    val stateOpacity = when {
        pressed -> 0.7f
        highlighted -> 0.8f
        else -> 1f
    }
    // Frosted material owns its opacity independently of an imported theme's alpha.
    // Disabled controls and transparent hit regions can explicitly scale the material.
    return tint.copy(alpha = opacity * stateOpacity * opacityScale.coerceIn(0f, 1f))
}
