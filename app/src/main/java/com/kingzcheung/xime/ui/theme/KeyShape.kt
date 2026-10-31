package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Key cap corners, for every layout and panel. ROUNDED keeps the configured radius (xime.yaml key.corner_radius,
 * 8 dp by default); SQUARE and SHARP cap it, so a theme that is already squarer stays as it is; ROUNDER raises it
 * to at least [ROUNDER_RADIUS] (10-31: someone's small-screen keyboard with rounder keys), larger radii stay.
 * Honeycomb letter keys keep their hexagons. IDs are stored; keep them upgrade compatible.
 */
enum class KeyShape(val id: String, val title: String, private val maxRadius: Int?, private val minRadius: Int? = null) {
    ROUNDED("rounded", "圆角", null),
    /** Rounder caps: 12 dp, about a third of a phone key's width, like a small-screen merged keyboard. */
    ROUNDER("rounder", "大圆角", null, ROUNDER_RADIUS),
    /** iOS 18 and earlier: small corners, same proportion to the key width. */
    SQUARE("square", "方形", 5),
    SHARP("sharp", "直角", 0);

    fun radius(configured: Int): Int {
        val capped = maxRadius?.let { minOf(it, configured) } ?: configured
        return minRadius?.let { maxOf(it, capped) } ?: capped
    }
    fun radius(configured: Dp): Dp {
        val capped = maxRadius?.let { minOf(it.dp, configured) } ?: configured
        return minRadius?.let { maxOf(it.dp, capped) } ?: capped
    }

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ROUNDED
    }
}

private const val ROUNDER_RADIUS = 12

object KeyShapes {
    var current by mutableStateOf(KeyShape.ROUNDED)
        internal set
}
