package com.kingzcheung.xime.service

import android.view.KeyEvent.*
import com.kingzcheung.xime.settings.HardwareKeyboardOptions
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.LanguageSwitchMode
import com.kingzcheung.xime.settings.LanguageSwitchOptions
import com.kingzcheung.xime.settings.LanguageSwitchPreferences
import org.junit.Assert.*
import org.junit.Test

/** Exercise the actual shortcut state machine and the policy used by the queued IME route. */
class HardwareLanguageSwitchRoutingTest {
    private val shortcuts = HardwareLanguageShortcuts()
    private val options = HardwareKeyboardOptions()
    private val zh = InputLanguage.CHINESE
    private val en = InputLanguage.ENGLISH
    private val ja = InputLanguage.JAPANESE
    private val order = listOf(zh, en, ja)

    private fun down(key: Int, time: Long = 0, repeat: Int = 0, ctrl: Boolean = false,
        shifted: Boolean = false, settings: HardwareKeyboardOptions = options) =
        shortcuts.down(key, time, repeat, ctrl, false, false, shifted, settings)

    private fun target(result: HardwareShortcutResult, current: InputLanguage,
        native: InputLanguage, globe: LanguageSwitchOptions,
        available: Set<InputLanguage> = order.toSet()): InputLanguage? {
        val mode = result.switchMode ?: return null
        return LanguageSwitchPreferences.target(current, native,
            LanguageSwitchPreferences.forRequest(globe, mode), order, available)
    }

    @Test fun shiftAlwaysReturnsToCurrentNativeLanguageRegardlessOfGlobeChoice() {
        for (globe in listOf(LanguageSwitchOptions(LanguageSwitchMode.CYCLE),
                LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, zh))) {
            for (native in listOf(zh, ja)) {
                shortcuts.reset()
                val press = down(KEYCODE_SHIFT_LEFT, shifted = true)
                assertTrue(press.consume)
                assertNull(press.switchMode)
                val release = shortcuts.up(KEYCODE_SHIFT_LEFT, 100, false, options)
                assertTrue(release.consume)
                assertEquals(LanguageSwitchMode.CURRENT_ENGLISH, release.switchMode)
                assertEquals(en, target(release, native, native, globe))

                down(KEYCODE_SHIFT_RIGHT, time = 200, shifted = true)
                val back = shortcuts.up(KEYCODE_SHIFT_RIGHT, 300, false, options)
                assertEquals(native, target(back, en, native, globe))
            }
        }
    }

    @Test fun ctrlSpaceCyclesSavedOrderAndConsumesEverySpaceEventOnlySwitchingOnce() {
        val globe = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, zh)
        var current = en
        var native = zh
        for (expected in listOf(ja, zh, en)) {
            val result = down(KEYCODE_SPACE, ctrl = true)
            assertTrue(result.consume)
            assertEquals(LanguageSwitchMode.CYCLE, result.switchMode)
            assertEquals(expected, target(result, current, native, globe))
            val repeated = down(KEYCODE_SPACE, repeat = 1, ctrl = true)
            assertTrue(repeated.consume)
            assertNull(repeated.switchMode)
            // Ctrl may be released before Space; the consumed Space must not leak to the editor.
            val release = shortcuts.up(KEYCODE_SPACE, 100, false, options)
            assertTrue(release.consume)
            assertNull(release.switchMode)
            current = expected
            if (expected != en) native = expected
        }
    }

    @Test fun cycleSkipsDisabledLanguagesWithoutChangingTheGlobePolicy() {
        val globe = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, ja)
        val cycle = down(KEYCODE_SPACE, ctrl = true)
        assertEquals(zh, target(cycle, en, ja, globe, setOf(zh, en)))
        assertNull(target(cycle, en, ja, globe, setOf(en)))
        // A subsequent globe tap keeps its independently selected Japanese/English pair.
        val globeRequest = LanguageSwitchPreferences.forRequest(globe, null)
        assertEquals(ja, LanguageSwitchPreferences.target(en, zh, globeRequest, order, order.toSet()))
        assertEquals(LanguageSwitchMode.SPECIFIC_ENGLISH, globe.mode)
        assertEquals(ja, globe.language)
    }

    @Test fun disablingEitherShortcutDoesNotDisableTheOther() {
        val shiftOnly = options.copy(ctrlSpace = false)
        assertFalse(down(KEYCODE_SPACE, ctrl = true, settings = shiftOnly).consume)
        shortcuts.up(KEYCODE_SPACE, 20, false, shiftOnly)
        down(KEYCODE_SHIFT_LEFT, time = 30, shifted = true, settings = shiftOnly)
        assertEquals(LanguageSwitchMode.CURRENT_ENGLISH,
            shortcuts.up(KEYCODE_SHIFT_LEFT, 100, false, shiftOnly).switchMode)

        shortcuts.reset()
        val cycleOnly = options.copy(shiftTap = false)
        assertFalse(down(KEYCODE_SHIFT_LEFT, shifted = true, settings = cycleOnly).consume)
        assertNull(shortcuts.up(KEYCODE_SHIFT_LEFT, 100, false, cycleOnly).switchMode)
        assertEquals(LanguageSwitchMode.CYCLE, down(KEYCODE_SPACE, ctrl = true, settings = cycleOnly).switchMode)
    }

    @Test fun shiftChordsNeverTriggerEitherLanguageAction() {
        down(KEYCODE_SHIFT_LEFT, shifted = true)
        assertFalse(down(KEYCODE_A, shifted = true).consume)
        shortcuts.up(KEYCODE_A, 50, false, options)
        assertNull(shortcuts.up(KEYCODE_SHIFT_LEFT, 100, false, options).switchMode)

        shortcuts.reset()
        down(KEYCODE_SHIFT_LEFT, shifted = true)
        down(KEYCODE_CTRL_LEFT, ctrl = true, shifted = true)
        val modifiedSpace = down(KEYCODE_SPACE, ctrl = true, shifted = true)
        assertFalse(modifiedSpace.consume)
        assertNull(modifiedSpace.switchMode)
        shortcuts.up(KEYCODE_SPACE, 50, false, options)
        shortcuts.up(KEYCODE_CTRL_LEFT, 60, false, options)
        assertNull(shortcuts.up(KEYCODE_SHIFT_LEFT, 100, false, options).switchMode)
    }
}
