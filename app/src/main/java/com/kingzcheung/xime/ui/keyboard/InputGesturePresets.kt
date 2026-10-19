package com.kingzcheung.xime.ui.keyboard

/**
 * 输入手势整套预设（用户 10-20 定，方案见 /mnt/project-files/输入手势预设方案.md）。
 * 预设只管下面这几项；选了预设后单独改其中任何一项，[of] 就认成 CUSTOM，与性能页同一做法。
 * 日文假名拂动与方向盘三套都一样（方向盘开，松手方向为准）。
 */
enum class InputGesturePreset(val label: String, val summary: String) {
    SWIPE("滑动", "上滑上角标、下滑下角标；九键上滑数字、长按选字母"),
    LONG_PRESS("长按", "长按出上角标、下滑下角标；九键长按数字、拂动选字母"),
    GLIDE("英文滑行", "同“滑动”，英文 26 键可以一笔划出整个词"),
    CUSTOM("自定义", "单独改过下面的某一项");

    /** 套用预设：只改预设管的几项，其他设置原样保留。CUSTOM 不改任何东西。 */
    fun applyTo(current: KeyboardInputPreferences): KeyboardInputPreferences = when (this) {
        SWIPE -> current.copy(symbolInputMode = SymbolInputMode.SWIPE_UP, reverseSymbolSwipe = false,
            letterLongPressMenu = false, glideTyping = false, t9FlickLetters = false,
            fourteenLetterSwipe = true, flickDirectionDisc = true, cursorGesture = CursorGestureMode.SPACE)
        LONG_PRESS -> SWIPE.applyTo(current).copy(symbolInputMode = SymbolInputMode.LONG_PRESS, t9FlickLetters = true)
        GLIDE -> SWIPE.applyTo(current).copy(glideTyping = true)
        CUSTOM -> current
    }

    companion object {
        val presets = listOf(SWIPE, LONG_PRESS, GLIDE)

        /** 当前设置正好是哪一套；对不上就是自定义。 */
        fun of(settings: KeyboardInputPreferences): InputGesturePreset =
            presets.firstOrNull { it.applyTo(settings) == settings } ?: CUSTOM
    }
}

/** 一个被锁住的选项值：[option] 不能设成 [lockedValue]，原因是 [by] 这个设置。 */
data class InputGestureLock(val option: Option, val lockedValue: Any, val by: String) {
    enum class Option { SYMBOL_INPUT_MODE, LETTER_LONG_PRESS_MENU, GLIDE_TYPING, CURSOR_GESTURE, T9_FLICK_LETTERS }

    /** 设置页在被锁的那一项下面显示这句。 */
    val message: String get() = "已被「$by」锁定"
}

/**
 * 明显冲突的设置互相加锁（用户 10-20 要求）：先设好的那一项把冲突的选项锁死，并说明是谁锁的。
 * - 角标“长按” ↔ 字母键长按气泡：长按总是出角标，气泡出不来。
 * - 英文滑行 ↔ 键盘区滑动移光标：两个都是在字母上拖动。
 * - 角标“长按”时九键拂动必须开：长按被数字占了，不开拂动就没法指定字母。
 * 角标“滑动” + 字母气泡不锁：这正是用户要的可选项，慢拖会被打断已写在说明里。
 */
object InputGestureLocks {
    fun of(settings: KeyboardInputPreferences): List<InputGestureLock> = buildList {
        if (settings.symbolInputMode == SymbolInputMode.LONG_PRESS) {
            add(InputGestureLock(InputGestureLock.Option.LETTER_LONG_PRESS_MENU, true, "角标输入方式：长按"))
            add(InputGestureLock(InputGestureLock.Option.T9_FLICK_LETTERS, false, "角标输入方式：长按"))
        }
        if (settings.letterLongPressMenu) {
            add(InputGestureLock(InputGestureLock.Option.SYMBOL_INPUT_MODE, SymbolInputMode.LONG_PRESS, "字母键长按弹出大小写"))
        }
        if (settings.glideTyping) {
            add(InputGestureLock(InputGestureLock.Option.CURSOR_GESTURE, CursorGestureMode.KEYBOARD, "英文滑行输入"))
        }
        if (settings.cursorGesture == CursorGestureMode.KEYBOARD) {
            add(InputGestureLock(InputGestureLock.Option.GLIDE_TYPING, true, "滑动移光标：键盘滑动"))
        }
    }

    /** 选“长按”角标时顺带打开九键拂动（否则九键没法选字母）；设置页改角标方式时用它代替直接 copy。 */
    fun withSymbolInputMode(settings: KeyboardInputPreferences, mode: SymbolInputMode): KeyboardInputPreferences =
        settings.copy(symbolInputMode = mode,
            t9FlickLetters = settings.t9FlickLetters || mode == SymbolInputMode.LONG_PRESS)

    /** 这个值能不能选；不能选时返回锁的原因，设置页据此置灰并显示 [InputGestureLock.message]。 */
    fun lockFor(settings: KeyboardInputPreferences, option: InputGestureLock.Option, value: Any): InputGestureLock? =
        of(settings).firstOrNull { it.option == option && it.lockedValue == value }
}
