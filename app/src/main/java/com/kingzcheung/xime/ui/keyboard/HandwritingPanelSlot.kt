package com.kingzcheung.xime.ui.keyboard

/**
 * 全屏手写时打开表情、编辑(方向)、符号、剪贴板等面板的位置（11-05 他报「全屏手写和方向、表情同时开会占满整个屏幕」）。
 * 面板回到平时键盘的大小和位置（底边、宽度、左右位置同全屏手写底部那排按键），上面照旧是全屏手写区，只是被面板让出一截。
 * 不是全屏手写时返回 null，面板照旧铺满键盘。
 */
internal data class HandwritingPanelSlot(val widthDp: Int, val heightDp: Int, val offsetXDp: Int)

internal fun handwritingPanelSlot(
    expanded: Boolean,
    normalHeightDp: Int,
    normalWidthDp: Int,
    normalOffsetXDp: Int,
    hostWidthDp: Int,
    hostHeightDp: Int,
): HandwritingPanelSlot? {
    if (!expanded || normalHeightDp <= 0 || hostHeightDp <= 0) return null
    val host = hostWidthDp.coerceAtLeast(1)
    val width = (if (normalWidthDp > 0) normalWidthDp else host).coerceIn(1, host)
    val travel = ((host - width) / 2).coerceAtLeast(0)
    return HandwritingPanelSlot(width, normalHeightDp.coerceAtMost(hostHeightDp), normalOffsetXDp.coerceIn(-travel, travel))
}
