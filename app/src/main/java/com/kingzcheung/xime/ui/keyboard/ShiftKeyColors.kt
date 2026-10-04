package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.viewmodel.ShiftMode

/** Glass Shift has a persistent, theme-tinted state, independent of press feedback. */
internal fun shiftKeyColors(
    background: Color,
    foreground: Color,
    accent: Color,
    panel: Color,
    config: FrostedGlassConfig,
    mode: ShiftMode,
    pressed: Boolean,
    legacyStateColor: Color,
): KeyboardKeyColors {
    val ordinary = frostedKeyColor(background, foreground, config, legacyStateColor,
        pressed = pressed, highlighted = mode != ShiftMode.OFF)
    if (!config.enabled || mode == ShiftMode.OFF) return KeyboardKeyColors(ordinary, foreground)

    val base = frostedKeyColor(background, foreground, config)
    val locked = mode == ShiftMode.CAPS
    val tint = if (locked) 0.65f else 0.35f
    val opacityBoost = (if (locked) 0.35f else 0.18f) + if (pressed) 0.08f else 0f
    // Fully transparent keys stay transparent; the icon still signals the state.
    val fill = lerp(base, accent, tint).copy(alpha = if (base.alpha == 0f) 0f
        else base.alpha + (1f - base.alpha) * opacityBoost)
    val surface = fill.compositeOver(panel.copy(alpha = 1f))
    fun contrast(color: Color): Float {
        val a = color.copy(alpha = 1f).luminance()
        val b = surface.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    val preferred = if (base.alpha == 0f) accent else foreground
    val icon = if (contrast(preferred) >= 3f) preferred.copy(alpha = 1f)
        else if (contrast(Color.Black) > contrast(Color.White)) Color.Black else Color.White
    return KeyboardKeyColors(fill, icon)
}
