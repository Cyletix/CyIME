package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material only. Theme owns brightness, colors and background. IDs remain upgrade compatible. */
enum class VisualStyle(val id: String, val title: String, val dark: Boolean? = null) {
    ORIGINAL("original", "原有外观"),
    NEON("neon", "霓光描边"),
    GLASS("glass", "流光玻璃"),
    FACET("facet", "柔光折面"),
    FROST("frost", "毛玻璃");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

object VisualStyles {
    var current by mutableStateOf(VisualStyle.ORIGINAL)
        internal set
}

fun Modifier.visualMaterial(style: VisualStyle, radius: Dp = 12.dp, level: MaterialLevel = MaterialLevel.BASE): Modifier =
    materialSurface(style, radius, level)
        // 键帽（普通与功能键）和卡片描边；浮层与无圆角的整条候选栏不描。
        .then(if (level != MaterialLevel.FLOATING && radius > 0.dp) blackKeyOutline(radius) else Modifier)

/** 黑白主题深色键帽和底色都是纯黑，键边只靠这一圈细白边分开（2026-10-22 用户：纯黑看不到边框）。 */
internal const val BLACK_KEY_OUTLINE_ALPHA = .34f

private fun Modifier.blackKeyOutline(radius: Dp): Modifier = composed {
    val palette = LocalKeyboardPalette.current
    if (palette == null || !palette.key.isPureBlack() || !palette.background.isPureBlack()) return@composed this
    drawWithCache {
        val stroke = density
        val inset = stroke / 2
        val corner = (radius.toPx() - inset).coerceIn(0f, size.minDimension / 2)
        val path = Path().apply { addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset),
            CornerRadius(corner))) }
        val color = Color.White.copy(alpha = BLACK_KEY_OUTLINE_ALPHA)
        onDrawWithContent {
            drawContent()
            if (size.width > stroke * 2 && size.height > stroke * 2) drawPath(path, color, style = Stroke(stroke))
        }
    }
}

private fun Color.isPureBlack() = alpha > .99f && red < .01f && green < .01f && blue < .01f
