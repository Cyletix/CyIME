package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.ClipboardCategorySwipe

/** Only the records region owns paging; tabs keep scrolling and headers keep resizing. */
internal fun Modifier.clipboardCategorySwipe(enabled: Boolean, onPage: (Int) -> Unit): Modifier = composed {
    val latestPage by rememberUpdatedState(onPage)
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val swipe = ClipboardCategorySwipe(viewConfiguration.touchSlop,
                maxOf(48.dp.toPx(), viewConfiguration.touchSlop * 3))
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed) return@awaitEachGesture
                val offset = change.position - down.position
                swipe.move(offset.x, offset.y)
                if (swipe.vertical) return@awaitEachGesture
                if (swipe.horizontal) change.consume()
                if (!change.pressed) {
                    val delta = swipe.pageDelta()
                    if (delta != 0) latestPage(delta)
                    return@awaitEachGesture
                }
            }
        }
    }
}
