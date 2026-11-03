package com.kingzcheung.xime.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Deliberate right-to-left navigation. A vertical start belongs to the list for the entire gesture. */
@Composable
internal fun Modifier.menuSwipeBack(
    page: Any?, onBack: (() -> Unit)?, pass: PointerEventPass = PointerEventPass.Main,
    /** false：从哪儿起手都算（键盘菜单的最后一页，和前面几页往左划翻页一样，11-03 声音振动上划不动）。 */
    edgeOnly: Boolean = true,
): Modifier {
    val latestBack by rememberUpdatedState(onBack)
    if (onBack == null) return this
    return pointerInput(page, pass, edgeOnly) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
            val edgeWidth = minOf(size.width * 0.25f, 96.dp.toPx())
            if (down.type != PointerType.Touch || (edgeOnly && down.position.x < size.width - edgeWidth)) return@awaitEachGesture
            val distance = (size.width * 0.22f).coerceIn(72.dp.toPx(), 112.dp.toPx())
            val slop = viewConfiguration.touchSlop
            var horizontal = false
            while (true) {
                val change = awaitPointerEvent(pass).changes.singleOrNull() ?: return@awaitEachGesture
                if (change.id != down.id || change.isConsumed) return@awaitEachGesture
                val left = down.position.x - change.position.x
                val vertical = abs(change.position.y - down.position.y)
                if (!horizontal) {
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                    if (maxOf(abs(left), vertical) < slop) {
                        if (!change.pressed) return@awaitEachGesture
                        continue
                    }
                }
                if (left <= 0f || left < vertical * 2.5f) return@awaitEachGesture
                horizontal = true
                change.consume()
                if (!change.pressed) {
                    if (left >= distance) latestBack?.invoke()
                    return@awaitEachGesture
                }
            }
        }
    }
}
