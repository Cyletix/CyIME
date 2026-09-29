package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
            VisualStyle.GLASS -> Color(0xFF30233F)
            VisualStyle.FACET -> Color(0xFF352C49)
            else -> Color(0xFFE7DEF4)
        }
        val foreground = if (light) Color(0xFF30203F) else Color(0xFFF4EDFF)
        val accent = if (light) Color(0xFF6940B0) else Color(0xFFD0ADFF)
        val function = if (light) Color(0xFFD4C1EF) else Color(0xFF51376F)
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

/** Cached paint only: no bitmap capture, live blur, extra input handlers or animation clocks. */
fun Modifier.visualMaterial(style: VisualStyle, radius: Dp = 12.dp, panel: Boolean = false): Modifier {
    if (style == VisualStyle.ORIGINAL) return this
    return drawWithCache {
        val light = style == VisualStyle.FROST
        val highlight = if (light) Color.White else Color(0xFFE6D2FF)
        val edge = if (light) Color(0xFF9273B8) else Color(0xFFB489F8)
        val alpha = when (style) {
            VisualStyle.NEON -> .04f
            VisualStyle.GLASS -> .18f
            VisualStyle.FACET -> .10f
            else -> .34f
        } * if (panel) .65f else 1f
        val sheen = Brush.linearGradient(listOf(highlight.copy(alpha = alpha), Color.Transparent,
            edge.copy(alpha = alpha * .5f)), Offset.Zero, Offset(size.width, size.height))
        val border = Brush.linearGradient(listOf(highlight.copy(alpha = if (style == VisualStyle.NEON) .65f else .30f),
            edge.copy(alpha = .10f), edge.copy(alpha = if (style == VisualStyle.NEON) .45f else .18f)))
        val stroke = 1.dp.toPx()
        val corner = CornerRadius(radius.toPx().coerceAtMost(size.minDimension / 2))
        val capOutline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
        val reflection = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * .24f)
            lineTo(0f, size.height * .68f)
            close()
        }
        onDrawWithContent {
            drawRoundRect(sheen, cornerRadius = corner)
            if (style == VisualStyle.GLASS || style == VisualStyle.FACET) {
                clipPath(capOutline) { drawPath(reflection, highlight.copy(alpha = if (panel) .025f else
                    if (style == VisualStyle.GLASS) .07f else .035f)) }
            }
            drawRoundRect(border, topLeft = Offset(stroke / 2, stroke / 2),
                size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                cornerRadius = corner, style = Stroke(stroke))
            drawContent() // Labels and icons remain sharp above the material.
        }
    }
}
