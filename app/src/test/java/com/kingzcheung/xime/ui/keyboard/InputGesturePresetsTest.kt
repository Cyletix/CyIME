package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputGesturePresetsTest {
    private val base = KeyboardInputPreferences(cursorGesture = CursorGestureMode.SPACE)

    @Test fun `defaults are the swipe preset and every preset is recognised after applying it`() {
        assertEquals(InputGesturePreset.SWIPE, InputGesturePreset.of(base))
        for (preset in InputGesturePreset.presets) assertEquals(preset, InputGesturePreset.of(preset.applyTo(base)))
    }

    @Test fun `changing one governed option turns the preset into custom`() {
        val swipe = InputGesturePreset.SWIPE.applyTo(base)
        assertEquals(InputGesturePreset.CUSTOM, InputGesturePreset.of(swipe.copy(letterLongPressMenu = true)))
        // 预设不管的设置（如按键字号、上下滑反转）改了不影响。
        assertEquals(InputGesturePreset.SWIPE, InputGesturePreset.of(swipe.copy(keyTextScale = 1.3f)))
        for (preset in InputGesturePreset.presets) {
            val reversed = preset.applyTo(base.copy(reverseSymbolSwipe = true))
            assertTrue("${preset.name} keeps reverse", reversed.reverseSymbolSwipe)
            assertEquals(preset, InputGesturePreset.of(reversed))
        }
    }

    /** 实际手势：上角标（九键数字）在 upText 槽位，经方向设置后落到哪个物理方向。 */
    private fun topSlotDirection(reverse: Boolean): String {
        val actions = KeyGestureActions("a", {}, {}, {}, {}, upText = "@", downText = "1")
            .withSymbolSwipeDirection(reverse)
        return if (actions.upText == "@") "上滑" else { assertEquals("@", actions.downText); "下滑" }
    }

    @Test fun `preset summaries name the direction the keys actually use`() {
        for (reverse in listOf(false, true)) {
            val top = topSlotDirection(reverse)
            val bottom = if (top == "上滑") "下滑" else "上滑"
            assertEquals("${top}上角标、${bottom}下角标；九键${top}数字、长按选字母",
                InputGesturePreset.SWIPE.summary(reverse))
            assertEquals("长按出上角标、${bottom}下角标；九键长按数字、拂动选字母",
                InputGesturePreset.LONG_PRESS.summary(reverse))
        }
        // 默认不反转：下滑出上角标和九键数字（B 站用户的情况）。
        assertEquals("下滑上角标、上滑下角标；九键下滑数字、长按选字母", InputGesturePreset.SWIPE.summary(false))
        assertEquals("上滑上角标、下滑下角标；九键上滑数字、长按选字母", InputGesturePreset.SWIPE.summary(true))
    }

    @Test fun `no preset contains a locked combination`() {
        for (preset in InputGesturePreset.presets) {
            val settings = preset.applyTo(base)
            for (lock in InputGestureLocks.of(settings)) {
                val value: Any = when (lock.option) {
                    InputGestureLock.Option.SYMBOL_INPUT_MODE -> settings.symbolInputMode
                    InputGestureLock.Option.LETTER_LONG_PRESS_MENU -> settings.letterLongPressMenu
                    InputGestureLock.Option.GLIDE_TYPING -> settings.glideTyping
                    InputGestureLock.Option.CURSOR_GESTURE -> settings.cursorGesture
                    InputGestureLock.Option.T9_FLICK_LETTERS -> settings.t9FlickLetters
                    InputGestureLock.Option.DOUBLE_PINYIN_KEY_HINTS -> settings.doublePinyinKeyHints
                    InputGestureLock.Option.CORNER_HINTS -> settings.swipeUpHints || settings.swipeDownHints
                    InputGestureLock.Option.SHIFT_SYLLABLE_SPLIT -> settings.shiftSyllableSplit
                    InputGestureLock.Option.SEMICOLON_ON_SHIFT -> settings.doublePinyinSemicolonOnShift
                }
                assertTrue("${preset.name} ${lock.option}", value != lock.lockedValue)
            }
        }
    }

    @Test fun `the setting chosen first locks the conflicting one and names itself`() {
        val longPress = base.copy(symbolInputMode = SymbolInputMode.LONG_PRESS)
        val bubbleLock = InputGestureLocks.lockFor(longPress, InputGestureLock.Option.LETTER_LONG_PRESS_MENU, true)
        assertNotNull(bubbleLock)
        assertEquals("已被「角标输入方式：长按」锁定", bubbleLock!!.message)
        assertNotNull(InputGestureLocks.lockFor(base.copy(letterLongPressMenu = true),
            InputGestureLock.Option.SYMBOL_INPUT_MODE, SymbolInputMode.LONG_PRESS))
        assertNotNull(InputGestureLocks.lockFor(base.copy(glideTyping = true),
            InputGestureLock.Option.CURSOR_GESTURE, CursorGestureMode.KEYBOARD))
        assertNotNull(InputGestureLocks.lockFor(base.copy(cursorGesture = CursorGestureMode.KEYBOARD),
            InputGestureLock.Option.GLIDE_TYPING, true))
        // 没有冲突的值不锁。
        assertNull(InputGestureLocks.lockFor(base, InputGestureLock.Option.LETTER_LONG_PRESS_MENU, true))
    }

    /** 用户 10-31：「这个开了那个就开不了」——双拼键位提示和上下角标提示、两个占 Shift 键的开关各自互锁。 */
    @Test fun `key hints and corner hints lock each other, so do the two shift key options`() {
        // 默认角标开着：键位提示被锁，原因写出是哪个角标。
        val hintLock = InputGestureLocks.lockFor(base, InputGestureLock.Option.DOUBLE_PINYIN_KEY_HINTS, true)
        assertEquals("已被「上方、下方角标提示」锁定", hintLock?.message)
        assertEquals("已被「下方角标提示」锁定", InputGestureLocks.lockFor(base.copy(swipeUpHints = false),
            InputGestureLock.Option.DOUBLE_PINYIN_KEY_HINTS, true)?.message)
        val clean = base.copy(swipeUpHints = false, swipeDownHints = false)
        assertNull(InputGestureLocks.lockFor(clean, InputGestureLock.Option.DOUBLE_PINYIN_KEY_HINTS, true))
        // 键位提示开着：两个角标提示都不能开。
        assertEquals("已被「双拼键位提示」锁定", InputGestureLocks.lockFor(clean.copy(doublePinyinKeyHints = true),
            InputGestureLock.Option.CORNER_HINTS, true)?.message)
        assertNull(InputGestureLocks.lockFor(clean, InputGestureLock.Option.CORNER_HINTS, true))
        // Shift：先开的锁住另一个。
        assertEquals("已被「ing 放在 Shift 键上」锁定", InputGestureLocks.lockFor(base.copy(doublePinyinSemicolonOnShift = true),
            InputGestureLock.Option.SHIFT_SYLLABLE_SPLIT, true)?.message)
        assertEquals("已被「Shift 键分词」锁定", InputGestureLocks.lockFor(base.copy(shiftSyllableSplit = true),
            InputGestureLock.Option.SEMICOLON_ON_SHIFT, true)?.message)
        assertNull(InputGestureLocks.lockFor(base, InputGestureLock.Option.SHIFT_SYLLABLE_SPLIT, true))
        assertNull(InputGestureLocks.lockFor(base, InputGestureLock.Option.SEMICOLON_ON_SHIFT, true))
    }

    @Test fun `choosing long press turns nine key flick on`() {
        assertTrue(InputGestureLocks.withSymbolInputMode(base, SymbolInputMode.LONG_PRESS).t9FlickLetters)
        assertEquals(false, InputGestureLocks.withSymbolInputMode(base, SymbolInputMode.SWIPE_UP).t9FlickLetters)
    }
}
