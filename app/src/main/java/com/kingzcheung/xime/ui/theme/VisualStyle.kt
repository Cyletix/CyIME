package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A rendering preset, independent of Rime themes and the user's saved color scheme. */
enum class VisualStyle(val id: String, val title: String, val dark: Boolean?) {
    ORIGINAL("original", "原有外观", null),
    NEON("neon", "霓光描边", true),
    GLASS("glass", "流光玻璃", true),
    FACET("facet", "柔光折面", true),
    FROST("frost", "浅色磨砂", false);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

object VisualStyles {
    var current by mutableStateOf(VisualStyle.ORIGINAL)
        internal set

    private val palettes = VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.associateWith { style ->
        val light = style == VisualStyle.FROST
        val background = if (light) Color(0xFFF3EFFA) else Color(0xFF14111F)
        val key = when (style) {
            VisualStyle.NEON -> Color(0xFF201A30)
            VisualStyle.GLASS -> Color(0x383F315B)
            VisualStyle.FACET -> Color(0xFF352C49)
            else -> Color(0xFFE7DEF4)
        }
        val foreground = if (light) Color(0xFF30203F) else Color(0xFFF4EDFF)
        val accent = if (light) Color(0xFF6940B0) else Color(0xFFD0ADFF)
        val function = if (light) Color(0xFFD4C1EF) else if (style == VisualStyle.GLASS) Color(0x604B356D) else Color(0xFF51376F)
        KeyboardColorScheme(
            id = "visual_${style.id}", name = style.title,
            specialKeyLight = function, specialKeyDark = function,
            accentLight = accent, accentDark = accent,
            primaryLight = accent, primaryDark = accent,
            primaryContainerLight = function, primaryContainerDark = function,
            surfaceLight = background, surfaceDark = background,
            keyboardBgLight = background, keyboardBgDark = background,
            keyBgLight = key, keyBgDark = key,
            candidateBarBgLight = background, candidateBarBgDark = background,
            keyTextColorLight = foreground, keyTextColorDark = foreground,
            candidateTextColorLight = accent, candidateTextColorDark = accent,
            candidateSelectedTextColorLight = foreground, candidateSelectedTextColorDark = foreground,
            specialKeyTextColorLight = foreground, specialKeyTextColorDark = foreground,
            enterKeyLight = function, enterKeyDark = function,
            useThemeColors = true,
        )
    }

    fun palette(style: VisualStyle): KeyboardColorScheme? = palettes[style]
}

/** Static material; independent of press feedback and input handlers. */
fun Modifier.visualMaterial(style: VisualStyle, radius: Dp = 12.dp, panel: Boolean = false): Modifier =
    materialSurface(style, radius, panel)
