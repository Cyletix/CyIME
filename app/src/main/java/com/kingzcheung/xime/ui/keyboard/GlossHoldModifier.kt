package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

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
 * 从这个键拖到候选栏上：经过候选时候选中英对调（[CandidateGlossary.t9DragSwap]），在候选上松手就按住
 * Shift 选它（有英文上屏英文）。松手判定在键自己的手势之前（Initial 阶段）消费掉这次抬起，所以键原来的
 * 左滑、上滑、点按都不会再触发；松手不在候选上则一律不碰，原手势照旧。
 */
internal fun Modifier.dragToCandidate(enabled: Boolean, targets: CandidateDropTargets = CandidateDropTargets.shared): Modifier =
    if (!enabled) this else composed {
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        onGloballyPositioned { coordinates[0] = it }.pointerInput(targets) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val swap = com.kingzcheung.xime.gloss.CandidateGlossary.t9DragSwap
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        val point = coordinates[0]?.takeIf { it.isAttached }?.localToRoot(change.position)
                        val target = point?.let { targets.at(it) }
                        swap.value = target != null
                        if (!change.pressed) {
                            if (target != null) {
                                change.consume()
                                target.second()
                            }
                            break
                        }
                    }
                } finally {
                    swap.value = false
                }
            }
        }
    }
