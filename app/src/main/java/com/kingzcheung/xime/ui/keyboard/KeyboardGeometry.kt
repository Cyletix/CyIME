package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt

/** 高度包含44dp工具栏与四行按键，不含悬浮拖条及系统导航栏。 */
internal fun keyboardHeightBounds(screenHeight: Int, landscape: Boolean, navInset: Int = 24): IntRange {
    val max = if (landscape) (screenHeight - navInset - FLOATING_DRAG_BAR_HEIGHT_DP - 24).coerceAtLeast(160)
        else (screenHeight * 45 / 100).coerceAtLeast(228)
    val min = if (landscape) 244.coerceAtMost(max) else (screenHeight * 28 / 100).coerceAtLeast(228).coerceAtMost(max)
    return min..max
}

internal fun floatingKeyboardWidth(screenWidth: Int, screenHeight: Int, height: Int, portraitHeight: Int, wide: Boolean): Int {
    val portraitWidth = minOf(screenWidth, screenHeight)
    val landscape = screenWidth > screenHeight
    val proportion = if (landscape) height.toFloat() / portraitHeight.coerceAtLeast(1) else 1f
    val expansion = if (landscape && wide) 1.2f else 1f
    return ((portraitWidth * 0.85f * proportion * expansion / 10).roundToInt() * 10)
        .coerceIn(minOf(260, screenWidth), screenWidth)
}
