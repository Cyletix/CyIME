package com.kingzcheung.xime.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * 鼠标滚轮（GitHub #103）：普通滚轮只发竖向滚动，横向的分页和卡片行收不到，外接鼠标滚了没反应。
 * 竖向滚动当成横向用（往下 = 往后），触控板横滑时横向分量更大，按横向算。到头了不吃掉事件，
 * 外面的竖向列表（设置页）照常滚；不循环。触摸拖动不经过这里。
 */

private fun wheelStep(delta: Offset): Float = if (abs(delta.x) > abs(delta.y)) delta.x else delta.y

/** 横向分页：滚一格翻一页。 */
internal fun Modifier.wheelPages(state: PagerState): Modifier = pointerInput(state) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val step = wheelStep(change.scrollDelta)
                if (step == 0f) continue
                val target = state.currentPage + if (step > 0f) 1 else -1
                if (target !in 0 until state.pageCount) continue
                change.consume()
                if (!state.isScrollInProgress) launch { state.animateScrollToPage(target) }
            }
        }
    }
}

/** 横向卡片行：滚一格移 64dp。 */
internal fun Modifier.wheelScroll(state: ScrollableState): Modifier = pointerInput(state) {
    val distance = 64.dp.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            val step = wheelStep(change.scrollDelta)
            if (step == 0f) continue
            if (state.dispatchRawDelta(step * distance) != 0f) change.consume()
        }
    }
}
