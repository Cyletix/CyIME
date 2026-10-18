package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.GestureAction

enum class SymbolInputMode(val label: String) {
    // Keep the stored enum name compatible with existing preferences.
    SWIPE_UP("滑动输入"), LONG_PRESS("长按输入")
}

/** Existing up/down configuration remains the top/bottom slot, including custom values.
 * Apply after long-press adaptation so its disabled top-slot gesture follows the same direction.
 * Corner-symbol keys and clear/undo follow this setting; kana flick gestures keep their contracts.
 */
internal fun KeyGestureActions.withSymbolSwipeDirection(reverse: Boolean): KeyGestureActions =
    if (reverse) this else copy(
        upText = downText, downText = upText,
        onUp = onDown, onDown = onUp,
        upPreviewText = downPreviewText, downPreviewText = upPreviewText,
        upPreviewFromTop = downPreviewFromTop, downPreviewFromTop = upPreviewFromTop,
        blockUpSwipe = blockDownSwipe, blockDownSwipe = blockUpSwipe,
    )

/** Function keys opt in explicitly; repeat, tap and horizontal editing actions stay unchanged. */
internal fun RepeatingKeyActions.withSymbolSwipeDirection(reverse: Boolean): RepeatingKeyActions =
    if (reverse) this else copy(
        onUp = onDown, onDown = onUp,
        upLabel = downLabel?.replaceFirst("下滑", "上滑"),
        downLabel = upLabel?.replaceFirst("上滑", "下滑"),
        swappedVertical = !swappedVertical,
    )

/** Only literal input opts in; editing commands and dedicated function keys keep their gestures. */
internal fun symbolInputValue(value: String?, action: GestureAction?): String? =
    value?.takeIf { it.isNotBlank() && (action == null || action == GestureAction.COMMIT) }

internal fun KeyGestureActions.withSymbolInput(mode: SymbolInputMode, symbol: String?): KeyGestureActions {
    if (mode != SymbolInputMode.LONG_PRESS || symbol.isNullOrBlank() || onUp == null ||
        onLongPress != null || longPressDrawableIds.isNotEmpty()) return this
    val commitSymbol = onUp
    return copy(
        onUp = null,
        blockUpSwipe = true,
        longPressTimeoutMillis = 300L,
        // The timeout callback commits once; no release-time menu may commit a second value.
        longPressItems = emptyList(),
        onLongPressSelect = null,
        onLongPress = { commitSymbol(symbol) },
    )
}
