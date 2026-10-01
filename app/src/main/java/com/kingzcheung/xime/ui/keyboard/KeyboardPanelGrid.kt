package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 菜单和方案共享受面板高度约束的网格；低高度时分页，卡片不能越界挤压下一行。 */
@Composable
internal fun <T> KeyboardPanelGrid(
    items: List<T>, isLandscape: Boolean, textColor: Color, pagerTag: String,
    modifier: Modifier = Modifier, compactCards: Boolean = false, preferSinglePage: Boolean = false, content: @Composable (T, Modifier) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier.padding(horizontal = if (isLandscape) 16.dp else 12.dp, vertical = 8.dp)) {
        val twoRowModes = compactCards && items.size > 2 && maxHeight >= 240.dp
        val minimumRowHeight = (34 + (if (preferSinglePage && LocalDensity.current.fontScale <= 1.3f) 14 else 28) * LocalDensity.current.fontScale).dp
        val lowWidePanel = maxWidth >= 560.dp && maxHeight < minimumRowHeight * 2 + 24.dp
        val menuColumns = ((maxWidth.value + 8f) / (64f * LocalDensity.current.fontScale + 8f))
            .toInt().coerceIn(1, if (lowWidePanel) 8 else 4)
        val columns = minOf(items.size, if (twoRowModes) 2 else if (compactCards) 4 else menuColumns)
        val spacing = 8.dp
        val indicatorHeight = 16.dp
        val rows = if (twoRowModes) 2 else if (items.size > columns &&
            maxHeight - (if (preferSinglePage && items.size <= columns * 2) 0.dp else indicatorHeight) >= minimumRowHeight * 2 + spacing) 2 else 1
        val pages = items.chunked(rows * columns)
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val pageIndicatorHeight = if (pages.size == 1) 0.dp else indicatorHeight
        val gridHeight = if (compactCards) minOf(maxHeight,
            maxOf(120.dp, minimumRowHeight) * rows + spacing * (rows - 1) + pageIndicatorHeight) else minOf(maxHeight,
            maxOf(104.dp, minimumRowHeight) * rows + spacing * (rows - 1) + pageIndicatorHeight)
        val gridWidth = if (compactCards) minOf(maxWidth, 180.dp * columns + spacing * (columns - 1)) else minOf(maxWidth, 144.dp * columns + spacing * (columns - 1))
        Column(Modifier.align(Alignment.Center).width(gridWidth).height(gridHeight), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pagerState, pageSpacing = spacing, modifier = Modifier.fillMaxWidth().weight(1f).testTag(pagerTag)) { page ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    repeat(rows) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            repeat(columns) { column ->
                                val cellModifier = Modifier.weight(1f).fillMaxHeight()
                                val item = pages[page].getOrNull(row * columns + column)
                                if (item == null) Spacer(cellModifier) else content(item, cellModifier)
                            }
                        }
                    }
                }
            }
            Row(Modifier.height(pageIndicatorHeight), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (pages.size > 1) repeat(pages.size) { index ->
                    Box(Modifier.size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape).background(if (index == pagerState.currentPage) textColor else textColor.copy(alpha = 0.3f)))
                }
            }
        }
    }
}
