package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import com.kingzcheung.xime.keyboard.GestureAction
import com.kingzcheung.xime.settings.DisplayMode
import com.kingzcheung.xime.settings.KeysConfigHelper
import kotlin.math.roundToInt

internal val LocalFourteenKeyLayout = staticCompositionLocalOf { false }

internal fun normalizeLetterSwipeDistance(value: Float): Float =
    (((value.takeIf { it.isFinite() } ?: 24f).coerceIn(12f, 72f) / 2f).roundToInt() * 2f)

/** Only paired alphabet keys participate; function keys and single letters never do. */
internal fun fourteenKeyLetters(group: String?, enabled: Boolean): Pair<String, String>? =
    group?.takeIf { enabled && it.length == 2 && it.all { char -> char.lowercaseChar() in 'a'..'z' } }
        ?.let { it[0].toString() to it[1].toString() }

/**
 * The corner symbols of one letter on a paired 14-key key. The left half is the key's own gesture (its first
 * letter); this is the right half, the second letter, so both letters keep the symbols they have on 26 keys.
 */
data class KeyHalfSymbols(
    val swipeText: String?,
    val swipeDownText: String?,
    val swipeUpKeyLabel: String?,
    val swipeDownKeyLabel: String?,
    val symbolInputText: String?,
    val onSwipe: ((String) -> Unit)?,
    val onSwipeDown: ((String) -> Unit)?,
)

/** Same label/value/display rules as the key's own swipes in [KeyboardRowWithConfig]. */
internal fun fourteenRightHalfSymbols(
    key: String,
    fourteenKey: Boolean,
    isAsciiMode: Boolean,
    swipeUpHintsEnabled: Boolean,
    swipeDownHintsEnabled: Boolean,
    commit: (String) -> Unit,
    onGestureAction: ((GestureAction, String) -> Unit)?,
): KeyHalfSymbols? {
    val letter = fourteenKeyLetters(key, fourteenKey)?.second ?: return null
    val gesture = KeysConfigHelper.getKeyGesture(letter, isAsciiMode) ?: return null
    fun action(label: String?, value: String?, kind: GestureAction?): ((String) -> Unit)? {
        val text = value?.ifEmpty { label } ?: label
        if (text.isNullOrEmpty() || kind == GestureAction.NONE) return null
        return { _ -> if (kind == null || kind == GestureAction.COMMIT) commit(text) else onGestureAction?.invoke(kind, text) }
    }
    val up = gesture.swipeUp
    val upLabel = up?.label?.ifEmpty { up.value }?.takeIf { swipeUpHintsEnabled }
    val down = gesture.swipeDown
    val downLabel = down?.label?.takeIf { it.isNotEmpty() }
    val downDisplay = down?.display ?: DisplayMode.BOTH
    return KeyHalfSymbols(
        swipeText = upLabel,
        swipeDownText = downLabel.takeIf { downDisplay != DisplayMode.KEY && swipeDownHintsEnabled },
        swipeUpKeyLabel = if ((up?.display ?: DisplayMode.BOTH) != DisplayMode.BUBBLE) upLabel else "",
        swipeDownKeyLabel = downLabel.takeIf { downDisplay != DisplayMode.BUBBLE && swipeDownHintsEnabled },
        symbolInputText = symbolInputValue(up?.value?.ifEmpty { up.label }, up?.action),
        onSwipe = action(up?.label, up?.value, up?.action),
        onSwipeDown = downLabel?.let { action(it, down?.value, down?.action) },
    )
}
