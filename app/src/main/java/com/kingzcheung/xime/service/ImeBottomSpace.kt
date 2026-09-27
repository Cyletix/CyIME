package com.kingzcheung.xime.service

import kotlin.math.ceil

/** Keep the complete system navigation/taskbar inset; use the same value on every frame. */
internal fun imeBottomSpaceDp(insetPx: Int, density: Float): Int =
    if (insetPx > 0 && density.isFinite() && density > 0f) ceil(insetPx / density).toInt() else 0
