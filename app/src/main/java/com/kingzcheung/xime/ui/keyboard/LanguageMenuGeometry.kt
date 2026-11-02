package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

internal val LocalLanguageMenuPanel = staticCompositionLocalOf { Rect.Zero }

@Composable
internal fun LanguageMenuPanel(content: @Composable () -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalLanguageMenuPanel provides bounds, content = content)
    }
}

/**
 * [anchoredAbove]：菜单贴在语言键正上方（底边对齐键的顶边），语言多时在菜单里滚动，不再往下盖住语言键。
 * 键上方连标题加一行都放不下时才退回整块面板里摆放。
 */
internal data class LanguageMenuGeometry(val bounds: Rect, val rowHeightPx: Float, val anchoredAbove: Boolean = false)

/** 标题行（28dp）加上下内边距（各 6dp），和 LanguageMenuCard 一致。 */
internal const val LANGUAGE_MENU_CHROME_DP = 40f

/** 贴键菜单不滚动也要整行放下的语言数。 */
internal const val LANGUAGE_MENU_MIN_VISIBLE_ROWS = 3

/** 贴键菜单为放下 [LANGUAGE_MENU_MIN_VISIBLE_ROWS] 行可以压到的最小行高（语言名 + 方案两行字）。 */
internal const val LANGUAGE_MENU_MIN_ROW_DP = 40f

internal fun languageMenuGeometry(panel: Rect, key: Rect, density: Float, count: Int): LanguageMenuGeometry {
    val d = density.coerceAtLeast(0.1f)
    if (panel.width <= 0f || panel.height <= 0f) return LanguageMenuGeometry(Rect.Zero, 44f * d)
    val margin = minOf(8f * d, panel.width / 4f, panel.height / 4f)
    val area = panel.deflate(margin)
    val width = (panel.width * 0.6f).coerceIn(minOf(160f * d, area.width), minOf(280f * d, area.width))
    val rowHeight = (panel.height * 0.23f).coerceIn(44f * d, 64f * d)
    val chrome = LANGUAGE_MENU_CHROME_DP * d
    val full = chrome + count.coerceAtLeast(1) * rowHeight
    // Fit within the remaining space before translating to window coordinates. A full axis has zero travel.
    val x = area.left + (key.right - area.left - width).coerceIn(0f, area.width - width)
    val above = minOf(key.top, area.bottom) - area.top
    // 键盘调到最低时键上方只放得下两行半，至少要整行放下三种语言（用户 11-02）：行高按键上方空间压一点，
    // 不低于 40dp（两行字的高度）；空间够时还是原来的行高。
    val visibleRows = count.coerceIn(1, LANGUAGE_MENU_MIN_VISIBLE_ROWS)
    val anchoredRow = ((above - chrome) / visibleRows).coerceIn(LANGUAGE_MENU_MIN_ROW_DP * d, rowHeight)
    if (key.height > 0f && above >= chrome + anchoredRow) {
        val height = minOf(chrome + count.coerceAtLeast(1) * anchoredRow, above)
        val bottom = minOf(key.top, area.bottom)
        return LanguageMenuGeometry(Rect(x, bottom - height, minOf(x + width, area.right), bottom), anchoredRow, true)
    }
    val height = minOf(full, area.height)
    val y = area.top + (key.top - area.top - height).coerceIn(0f, area.height - height)
    return LanguageMenuGeometry(Rect(x, y, minOf(x + width, area.right), minOf(y + height, area.bottom)), rowHeight)
}

/**
 * 菜单刚弹出时列表的滚动位置。贴在键上方时当前语言放在最下面一行，紧挨着按住语言键的手指；
 * 退回整块面板时沿用原来的做法，当前语言在最上面。
 */
internal fun languageMenuInitialScroll(geometry: LanguageMenuGeometry, currentIndex: Int, density: Float): Int {
    val index = currentIndex.coerceAtLeast(0)
    val row = geometry.rowHeightPx
    if (!geometry.anchoredAbove) return (index * row).toInt()
    val list = geometry.bounds.height - LANGUAGE_MENU_CHROME_DP * density.coerceAtLeast(0.1f)
    return ((index + 1) * row - list).coerceAtLeast(0f).roundToInt()
}
