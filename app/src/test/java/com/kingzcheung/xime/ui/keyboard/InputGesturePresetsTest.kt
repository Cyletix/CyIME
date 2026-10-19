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
        assertEquals(InputGesturePreset.CUSTOM, InputGesturePreset.of(swipe.copy(reverseSymbolSwipe = true)))
        // 预设不管的设置（如按键字号）改了不影响。
        assertEquals(InputGesturePreset.SWIPE, InputGesturePreset.of(swipe.copy(keyTextScale = 1.3f)))
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

    @Test fun `choosing long press turns nine key flick on`() {
        assertTrue(InputGestureLocks.withSymbolInputMode(base, SymbolInputMode.LONG_PRESS).t9FlickLetters)
        assertEquals(false, InputGestureLocks.withSymbolInputMode(base, SymbolInputMode.SWIPE_UP).t9FlickLetters)
    }
}
