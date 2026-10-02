package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.GestureAction

enum class SymbolInputMode(val label: String) {
    SWIPE_UP("上滑输入"), LONG_PRESS("长按输入")
}

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
