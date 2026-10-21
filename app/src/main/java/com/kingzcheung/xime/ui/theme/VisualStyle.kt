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
    }
}

object VisualStyles {
    var current by mutableStateOf(VisualStyle.ORIGINAL)
        internal set
}

fun Modifier.visualMaterial(style: VisualStyle, radius: Dp = 12.dp, level: MaterialLevel = MaterialLevel.BASE): Modifier =
    materialSurface(style, radius, level)
