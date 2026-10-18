package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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
