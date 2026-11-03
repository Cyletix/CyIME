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
    /** 退一层要划多远；null 按宽度的 22%，夹在 72–112dp。 */
    distanceDp: Float? = null,
    /**
     * 按下那一刻算不算数。会随手势变化的条件（翻页能不能往后翻）放这里，不能拿它把 [onBack] 换成 null：
     * 手势中途把这个节点摘掉，系统会连带取消里面翻页的那一划（11-03 平板：第二页往右划回不到第一页）。
     */
    active: () -> Boolean = { true },
): Modifier {
    val latestBack by rememberUpdatedState(onBack)
    val latestActive by rememberUpdatedState(active)
    if (onBack == null) return this
    return pointerInput(page, pass, edgeOnly, distanceDp) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
            if (!latestActive()) return@awaitEachGesture
            val edgeWidth = minOf(size.width * 0.25f, 96.dp.toPx())
            if (down.type != PointerType.Touch || (edgeOnly && down.position.x < size.width - edgeWidth)) return@awaitEachGesture
            val distance = distanceDp?.dp?.toPx() ?: (size.width * 0.22f).coerceIn(72.dp.toPx(), 112.dp.toPx())
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
