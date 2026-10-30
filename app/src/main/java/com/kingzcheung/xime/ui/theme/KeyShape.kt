package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Key cap corners, for every layout and panel. ROUNDED keeps the configured radius (xime.yaml key.corner_radius,
 * 8 dp by default); the others cap it, so a theme that is already squarer stays as it is.
 * Honeycomb letter keys keep their hexagons. IDs are stored; keep them upgrade compatible.
 */
enum class KeyShape(val id: String, val title: String, private val maxRadius: Int?) {
    ROUNDED("rounded", "圆角", null),
    /** iOS 18 and earlier: small corners, same proportion to the key width. */
    SQUARE("square", "方形", 5),
    SHARP("sharp", "直角", 0);

    fun radius(configured: Int): Int = maxRadius?.let { minOf(it, configured) } ?: configured
    fun radius(configured: Dp): Dp = maxRadius?.let { minOf(it.dp, configured) } ?: configured

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ROUNDED
    }
}

object KeyShapes {
    var current by mutableStateOf(KeyShape.ROUNDED)
        internal set
}
