package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.settings.LayoutKind
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 布局手势开关（[ToolbarButton.LAYOUT_GESTURES]）和普通入口一样可在「排列」里隐藏、排序；
 * 只在适用的布局出现（排列页同样只列出当前键盘会出现的）；这里决定适用布局、开关状态和存储键。
 */
internal fun ToolbarButton.appliesTo(kind: LayoutKind): Boolean = when (this) {
    ToolbarButton.GLIDE -> kind == LayoutKind.ALPHABETIC
    ToolbarButton.T9_FLICK -> kind == LayoutKind.T9
    ToolbarButton.FOURTEEN_SWIPE -> kind == LayoutKind.MERGED
    ToolbarButton.KANA_HINTS -> kind == LayoutKind.KANA_KEYPAD
    ToolbarButton.FLICK_DISC -> kind in setOf(LayoutKind.T9, LayoutKind.MERGED, LayoutKind.KANA_KEYPAD)
    else -> true
}

internal fun ToolbarButton.gestureOn(prefs: KeyboardInputPreferences): Boolean = when (this) {
    ToolbarButton.GLIDE -> prefs.glideTyping
    ToolbarButton.T9_FLICK -> prefs.t9FlickLetters
    ToolbarButton.FOURTEEN_SWIPE -> prefs.fourteenLetterSwipe
    ToolbarButton.KANA_HINTS -> prefs.kanaDirectionHints
    ToolbarButton.FLICK_DISC -> prefs.flickDirectionDisc
    else -> false
}

/** 同一含义的全部存储键（旧键随新键一起写，保持读取兼容）。 */
internal fun ToolbarButton.gestureKeys(): List<String> = when (this) {
    ToolbarButton.GLIDE -> listOf("glide_typing_v2", "glide_typing")
    ToolbarButton.T9_FLICK -> listOf("t9_flick_letters")
    ToolbarButton.FOURTEEN_SWIPE -> listOf("fourteen_letter_swipe")
    ToolbarButton.KANA_HINTS -> listOf("kana_direction_hints")
    ToolbarButton.FLICK_DISC -> listOf("flick_direction_disc_v2", "flick_direction_disc")
    else -> emptyList()
}

/** 只改这一项的键，避免用整份偏好覆盖其他设置刚写入的值。 */
internal fun ToolbarButton.flipGesture(context: Context, prefs: KeyboardInputPreferences) {
    val next = !gestureOn(prefs)
    // 和设置页同一套锁：被别的设置锁住时不改，提示是谁锁的。
    val option = when (this) {
        ToolbarButton.GLIDE -> InputGestureLock.Option.GLIDE_TYPING
        ToolbarButton.T9_FLICK -> InputGestureLock.Option.T9_FLICK_LETTERS
        else -> null
    }
    val lock = option?.let { InputGestureLocks.lockFor(prefs, it, next) }
    if (lock != null) {
        android.widget.Toast.makeText(context, "$label${lock.message}", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val edit = SettingsPreferences.getPrefsPublic(context).edit()
    gestureKeys().forEach { edit.putBoolean(it, next) }
    edit.apply()
}
