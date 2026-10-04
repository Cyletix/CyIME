package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.keyboard.RepeatInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class RepeatingKeyActions(
    val onTap: () -> Unit,
    val onPress: () -> Unit,
    val onRelease: () -> Unit,
    val onPreview: (SwipeState) -> Unit,
    val onRepeat: (() -> Unit)? = null,
    val onUp: (() -> Unit)? = null,
    val onDown: (() -> Unit)? = null,
    val onLeft: (() -> Unit)? = null,
    val upLabel: String? = null,
    val downLabel: String? = null,
)

/** A single owner for hold and swipe: touch slop must not cancel repeated deletion. */
internal suspend fun PointerInputScope.detectRepeatingKeyGestures(
    currentActions: () -> RepeatingKeyActions,
) = coroutineScope {
    val scope = this
    val actionDistance = 80.dp.toPx()
    val previewDistance = 50.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val actions = currentActions()
        val repeat = RepeatInput()
        var repeated = false
        var crossed = false
        down.consume()
        actions.onPress()
        actions.onPreview(SwipeState(isPressed = true))
        val timer = actions.onRepeat?.let { onRepeat -> scope.launch {
            delay(viewConfiguration.longPressTimeoutMillis)
            repeated = true
            while (repeat.isActive) {
                actions.onPress()
                repeat.dispatch(onRepeat)
                delay(30)
            }
        } }
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                val vertical = abs(dy) > abs(dx) * 1.1f
                val up = vertical && dy < -actionDistance && actions.onUp != null
                val downSwipe = vertical && dy > actionDistance && actions.onDown != null
                val left = -dx > abs(dy) * 1.1f && dx < -actionDistance && actions.onLeft != null
                val flick = keyFlickPreview(dx, dy, previewDistance, actionDistance,
                    actions.upLabel?.removePrefix("上滑").takeIf { actions.onUp != null },
                    actions.downLabel?.removePrefix("下滑").takeIf { actions.onDown != null })
                    ?.let { it.copy(origin = if (dy < 0f) KeyFlickOrigin.TOP else KeyFlickOrigin.BOTTOM) }
                if (flick != null) {
                    repeat.stop()
                    timer?.cancel()
                }
                if (abs(dx) > actionDistance || abs(dy) > actionDistance) {
                    crossed = true
                    repeat.stop()
                    timer?.cancel()
                }
                val hint = when {
                    vertical && dy < -previewDistance && actions.onUp != null -> actions.upLabel
                    vertical && dy > previewDistance && actions.onDown != null -> actions.downLabel
                    else -> null
                }
                actions.onPreview(SwipeState(isPressed = !crossed, isSwiping = hint != null,
                    swipeText = hint, isSwipeDown = dy > 0, isDanger = up || downSwipe, keyFlick = flick))
                change.consume()
                if (!change.pressed) {
                    // Stop queued repetitions before committing a release action.
                    repeat.stop()
                    timer?.cancel()
                    when {
                        up -> actions.onUp?.invoke()
                        downSwipe -> actions.onDown?.invoke()
                        left -> actions.onLeft?.invoke()
                        !repeated && !crossed -> actions.onTap()
                    }
                    break
                }
            }
        } finally {
            repeat.stop()
            timer?.cancel()
            actions.onRelease()
            actions.onPreview(SwipeState())
        }
    }
}
