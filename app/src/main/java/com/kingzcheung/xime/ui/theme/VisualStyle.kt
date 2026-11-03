package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

        /**
         * 视觉样式里能选的三个（11-03）。「流光玻璃」「毛玻璃」是没有真玻璃时画上去的假玻璃高光，
         * 和材质效果里的「玻璃效果」「液态玻璃」重复，不再提供。
         * id 和枚举项保留：桌面图标仍有这两种图标，旧设置读得回来。
         */
        val offered = listOf(ORIGINAL, NEON, FACET)

        /** 键盘和设置页实际画的材质：已不提供的两种按「原有外观」画。 */
        fun forKeyboard(style: VisualStyle) = if (style in offered) style else ORIGINAL
    }
}

object VisualStyles {
    var current by mutableStateOf(VisualStyle.ORIGINAL)
        internal set
}

fun Modifier.visualMaterial(style: VisualStyle, radius: Dp = 12.dp, level: MaterialLevel = MaterialLevel.BASE,
    bottomRadius: Dp = radius): Modifier =
    materialSurface(style, radius, level, bottomRadius)
