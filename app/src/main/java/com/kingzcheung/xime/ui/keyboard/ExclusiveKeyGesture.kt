package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 一次按下只触发一个输入分支；长按动作到时执行，滑动与菜单松手确认。 */
internal data class KeyGestureActions(
    val text: String,
    val onTap: () -> Unit,
    val onPress: () -> Unit,
    val onRelease: () -> Unit,
    val onPreview: (SwipeState) -> Unit,
    val upText: String? = null,
    val downText: String? = null,
    val onUp: ((String) -> Unit)? = null,
    val onDown: ((String) -> Unit)? = null,
    val leftText: String? = null,
    val onLeft: (() -> Unit)? = null,
    val rightText: String? = null,
    val onRight: (() -> Unit)? = null,
    val horizontalThresholdDp: Float = 50f,
    val reserveHorizontalSwipe: Boolean = false,
    val longPressItems: List<String> = emptyList(),
    val longPressDrawableIds: List<Int> = emptyList(),
    val keyWidth: Float = 1f,
    val onLongPress: (() -> Unit)? = null,
    val onLongPressSelect: ((String) -> Unit)? = null,
    val onLongPressFeedback: () -> Unit = {},
    val longPressTimeoutMillis: Long? = null,
    val blockUpSwipe: Boolean = false,
    val blockDownSwipe: Boolean = false,
    val inlineVerticalPreview: Boolean = false,
    val inlineHorizontalPreview: Boolean = false,
    val upPreviewText: String? = upText,
    val downPreviewText: String? = downText,
    val upPreviewFromTop: Boolean = true,
    val downPreviewFromTop: Boolean = false,
)

internal suspend fun PointerInputScope.detectExclusiveKeyGestures(
    currentActions: () -> KeyGestureActions,
) = coroutineScope {
    val gestureScope = this
    val swipeThreshold = 50.dp.toPx()
    val horizontalCancelThreshold = 60.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val actions = currentActions()
        val horizontalThreshold = actions.horizontalThresholdDp.dp.toPx()
        down.consume()
        actions.onPress()
        actions.onPreview(SwipeState(isPressed = true, pressedText = actions.text))
        var longPressed = false
        var moved = false
        var crossedSwipeThreshold = false
        var selectedIndex = 0
        val items = actions.longPressItems
        val longPressJob = if (items.isNotEmpty() || actions.onLongPress != null) gestureScope.launch {
            delay(actions.longPressTimeoutMillis ?: if (items.isNotEmpty()) 400L else viewConfiguration.longPressTimeoutMillis)
            longPressed = true
            actions.onLongPressFeedback()
            if (items.isNotEmpty()) {
                actions.onPreview(SwipeState(isPressed = true, isLongPress = true,
                    longPressItems = items, selectedLongPressIndex = selectedIndex,
                    longPressDrawableIds = actions.longPressDrawableIds))
            } else actions.onLongPress?.invoke()
        } else null
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!longPressed && (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop)) {
                    moved = true
                    longPressJob?.cancel()
                }
                val up = dy < -swipeThreshold && abs(dy) > abs(dx) * 1.1f && actions.onUp != null
                val downSwipe = dy > swipeThreshold && dy > abs(dx) * 1.1f && actions.onDown != null
                val left = dx < -horizontalThreshold && -dx > abs(dy) * 1.1f && actions.onLeft != null
                val right = dx > horizontalThreshold && dx > abs(dy) * 1.1f && actions.onRight != null
                val blockedUp = actions.blockUpSwipe && dy < -swipeThreshold && abs(dy) > abs(dx) * 1.1f
                val blockedDown = actions.blockDownSwipe && dy > swipeThreshold && dy > abs(dx) * 1.1f
                crossedSwipeThreshold = crossedSwipeThreshold || up || downSwipe || left || right || blockedUp || blockedDown
                if (longPressed && items.isNotEmpty()) {
                    val itemWidth = (actions.keyWidth / items.size).coerceAtLeast(1f)
                    selectedIndex = ((dx / itemWidth) + if (items.size > 1) 0.5f else 0f)
                        .toInt().coerceIn(items.indices)
                    actions.onPreview(SwipeState(isPressed = true, isLongPress = true,
                        longPressItems = items, selectedLongPressIndex = selectedIndex,
                        longPressDrawableIds = actions.longPressDrawableIds))
                } else if (!longPressed) {
                    val hint = if (up) actions.upText else if (downSwipe) actions.downText else if (left) actions.leftText else if (right) actions.rightText else null
                    actions.onPreview(SwipeState(isSwiping = hint != null, swipeText = hint,
                        isSwipeDown = downSwipe, isPressed = !moved, pressedText = actions.text,
                        keyFlick = (if (actions.inlineVerticalPreview) keyFlickPreview(
                            dx, dy, viewConfiguration.touchSlop, swipeThreshold,
                            actions.upPreviewText.takeIf { actions.onUp != null && !actions.blockUpSwipe },
                            actions.downPreviewText.takeIf { actions.onDown != null && !actions.blockDownSwipe },
                            actions.upPreviewFromTop, actions.downPreviewFromTop,
                        ) else null) ?: if (actions.inlineHorizontalPreview) horizontalKeyFlickPreview(
                            dx, dy, viewConfiguration.touchSlop, horizontalThreshold,
                            actions.leftText.takeIf { actions.onLeft != null },
                            actions.rightText.takeIf { actions.onRight != null },
                        ) else null))
                }
                if (!change.pressed) {
                    change.consume()
                    when {
                        longPressed -> items.getOrNull(selectedIndex)?.let { actions.onLongPressSelect?.invoke(it) }
                        up -> actions.onUp?.invoke(actions.upText.orEmpty())
                        downSwipe -> actions.onDown?.invoke(actions.downText.orEmpty())
                        left -> actions.onLeft?.invoke()
                        right -> actions.onRight?.invoke()
                        !crossedSwipeThreshold && abs(dx) < horizontalCancelThreshold -> actions.onTap()
                    }
                    break
                }
                // Reserve paired-letter drags before the whole-keyboard cursor's
                // activation distance, even when the user sets a longer threshold.
                if (longPressed || crossedSwipeThreshold || actions.reserveHorizontalSwipe &&
                    abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 1.1f) change.consume()
                else {
                    // 父层光标/滚动手势可能在 Main 后半段消费；取消后不得补出普通字符。
                    val finalChange = awaitPointerEvent(PointerEventPass.Final)
                        .changes.firstOrNull { it.id == down.id }
                    if (finalChange == null || finalChange.isConsumed) break
                }
            }
        } finally {
            longPressJob?.cancel()
            actions.onRelease()
            actions.onPreview(SwipeState())
        }
    }
}
