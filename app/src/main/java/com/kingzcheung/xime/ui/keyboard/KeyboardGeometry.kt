package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt

/**
 * 悬浮键盘大屏宽度基准与共用面板最小高度。
 *
 * 手机上宽度通过 floatingResizeMinWidthDp 自适应，避免 400dp 把宽度锁成全屏；
 * 高度仍保持两个方向共用同一基准，尺寸调节不切换布局比例。
 */
internal const val FLOATING_RESIZE_MIN_WIDTH_DP = 400
internal const val FLOATING_RESIZE_MIN_HEIGHT_DP = 228

/**
 * 悬浮调节专用高度范围。
 *
 * 不能复用普通键盘的 keyboardHeightBounds：普通键盘的范围很窄，悬浮调节会几乎拖不动；
 * 也不能直接放到整屏高，否则九键会被拉成夸张的长条。
 * 下限恒为 [FLOATING_RESIZE_MIN_HEIGHT_DP]；可用高度不足时由调用方按
 * [floatingResizeMinHeightDp] 夹到可用区，不产生越界初值。
 */
internal fun floatingResizeHeightBounds(hostHeightDp: Int, landscape: Boolean): IntRange {
    val host = hostHeightDp.coerceAtLeast(1)
    val min = FLOATING_RESIZE_MIN_HEIGHT_DP
    // 放宽大屏纵向调节空间；独立的高宽比上限继续防止拉成长柱。
    val maxByScreen = (host * 70 / 100) - FLOATING_DRAG_BAR_HEIGHT_DP
    val max = maxByScreen.coerceAtLeast(min)
    return min..max
}

/** 最小宽度：手机基准下限与可用宽度取小（[availableWidthDp] 已扣边距）。 */
internal fun floatingResizeMinWidthDp(availableWidthDp: Int): Int =
    availableWidthDp.coerceAtLeast(1).let { available ->
        // 400dp 在手机上会被夹成全屏宽，导致没有缩小空间；大屏仍保留可用尺寸。
        (available * 0.65f).roundToInt().coerceIn(260, FLOATING_RESIZE_MIN_WIDTH_DP)
            .coerceAtMost(available)
    }

/** 最小高度：手机基准下限与可用高度取小（[availableHeightDp] 已扣边距）。 */
internal fun floatingResizeMinHeightDp(availableHeightDp: Int): Int =
    minOf(FLOATING_RESIZE_MIN_HEIGHT_DP, availableHeightDp.coerceAtLeast(1))

/** 悬浮键盘允许的最大高宽比。只限制畸形，不强制等比缩放。 */
internal fun floatingResizeMaxAspect(landscape: Boolean): Float = if (landscape) 0.95f else 1.35f

/** 高度包含44dp工具栏与四行按键，不含悬浮拖条及系统导航栏。 */
internal fun keyboardHeightBounds(screenHeight: Int, landscape: Boolean, navInset: Int = 24): IntRange {
    val max = if (landscape) (screenHeight - navInset - FLOATING_DRAG_BAR_HEIGHT_DP - 24).coerceAtLeast(160)
        else (screenHeight * 45 / 100).coerceAtLeast(228)
    // 固定与悬浮共享面板下限，不能随平板屏幕高度放大最小值。
    val min = FLOATING_RESIZE_MIN_HEIGHT_DP.coerceAtMost(max)
    return min..max
}

internal fun floatingKeyboardWidth(screenWidth: Int, screenHeight: Int, height: Int, portraitHeight: Int, wide: Boolean): Int {
    val landscape = screenWidth > screenHeight
    // 没有用户保存尺寸时给一个真正可用的默认值：竖屏约 78%，横屏约 48% 屏宽。
    // 不再从普通键盘高度反推宽度，避免横屏默认尺寸忽大忽小。
    val ratio = if (landscape) (if (wide) 0.52f else 0.48f) else 0.78f
    return ((screenWidth * ratio / 10f).roundToInt() * 10)
        .coerceIn(keyboardWidthBounds(screenWidth))
}

/** 悬浮键盘宽度范围（dp）。 */
internal fun keyboardWidthBounds(screenWidth: Int): IntRange {
    val cap = screenWidth.coerceAtLeast(1)
    return floatingResizeMinWidthDp(cap)..cap
}

/**
 * 悬浮卡片宽度解析：设置过宽度就用设置值（夹在 [keyboardWidthBounds] 内），
 * 未设置（0）时按高度推导，保持历史外观。
 */
internal fun resolvedFloatingWidth(
    screenWidth: Int,
    screenHeight: Int,
    height: Int,
    portraitHeight: Int,
    wide: Boolean,
    overrideWidth: Int,
): Int = if (overrideWidth > 0) {
    overrideWidth.coerceIn(keyboardWidthBounds(screenWidth))
} else {
    floatingKeyboardWidth(screenWidth, screenHeight, height, portraitHeight, wide)
}

/** 固定卡片与调整框共同解析旧尺寸；0 仍表示全宽。 */
internal fun resolvedFixedKeyboardWidth(availableWidthDp: Int, savedWidthDp: Int): Int =
    if (savedWidthDp > 0) savedWidthDp.coerceIn(keyboardWidthBounds(availableWidthDp))
    else availableWidthDp.coerceAtLeast(1)
