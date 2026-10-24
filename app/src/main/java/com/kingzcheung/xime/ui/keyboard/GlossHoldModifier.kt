package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

/**
 * 按住这个键超过 [timeoutMillis] 且没有滑动算一次长按：[onHoldChange] 收到 true，松手时收到 false。
 * 只旁听不消费触摸，键原来的点按、滑动照旧；[onHoldChange] 收到 true 后，这次松手的点按应由调用方忽略，
 * [onDown] 在每次按下时调用，用来清掉上一次的忽略标记。
 */
internal fun Modifier.glossHold(
    enabled: Boolean,
    timeoutMillis: Long = 300L,
    onDown: () -> Unit = {},
    onHoldChange: (Boolean) -> Unit,
): Modifier = if (!enabled) this else pointerInput(timeoutMillis) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown()
        var held = false
        try {
            // 计时内松手或滑出去：普通点按或滑动，不算按住。
            val ended = withTimeoutOrNull(timeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (ended == null) {
                held = true
                onHoldChange(true)
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            }
        } finally {
            if (held) onHoldChange(false)
        }
    }
}

/**
 * 从这个键拖到候选栏上：手指离开按下点超过 [area]（键盘区）宽或高的一半才算拖动选词（用户 10-25），
 * 没过这个距离仍是键自己的左滑、上滑、点按。进入拖动后在 Initial 阶段消费触摸，键自己的手势随即取消
 * （不再出「上一项」等预览，松手也不触发）；候选栏和键盘都画出从键到手指的拖线（[CandidateDropTargets.dragLine]），
 * 指着的候选高亮并中英对调（[CandidateGlossary.t9DragSwap]），在候选上松手就按住 Shift 选它（有英文上屏英文），
 * 松手不在候选上则什么都不做。
 */
internal fun Modifier.dragToCandidate(
    enabled: Boolean,
    area: () -> Rect,
    targets: CandidateDropTargets = CandidateDropTargets.shared,
): Modifier = if (!enabled) this else composed {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentArea by rememberUpdatedState(area)
    onGloballyPositioned { coordinates[0] = it }.pointerInput(targets) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val swap = com.kingzcheung.xime.gloss.CandidateGlossary.t9DragSwap
            var dragging = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    val layout = coordinates[0]?.takeIf { it.isAttached }
                    if (!dragging) dragging = isCandidateDrag(change.position - down.position, currentArea().size)
                    if (!dragging || layout == null) {
                        if (!change.pressed) break
                        continue
                    }
                    change.consume()
                    val point = layout.localToRoot(change.position)
                    val target = targets.at(point)
                    swap.value = target != null
                    targets.dragLine = layout.localToRoot(Offset(size.width / 2f, size.height / 2f)) to point
                    targets.hoveredBounds = target?.first
                    if (!change.pressed) {
                        target?.second?.invoke()
                        break
                    }
                }
            } finally {
                swap.value = false
                if (dragging) {
                    targets.dragLine = null
                    targets.hoveredBounds = null
                }
            }
        }
    }
}

/** 离按下点超过键盘区宽或高的一半：算拖动选词，不再是键内滑动。 */
internal fun isCandidateDrag(delta: Offset, area: Size): Boolean =
    area.width > 0f && area.height > 0f && (abs(delta.x) > area.width / 2f || abs(delta.y) > area.height / 2f)
