package com.kingzcheung.xime.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShiftStateTest {
    @Test
    fun `single shift affects one character then returns to lowercase`() {
        val shift = KeyboardShiftState()
        assertFalse(shift.mode.value.isShifted)
        shift.singleTap()
        assertEquals(ShiftMode.SINGLE, shift.mode.value)
        assertTrue(shift.mode.value.isShifted)
        shift.onCharacterTyped()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        assertFalse(shift.mode.value.isShifted)
    }

    @Test
    fun `double tap locks uppercase through multiple characters`() {
        val shift = KeyboardShiftState()
        // The button first dispatches a single tap, then upgrades the second tap to caps lock.
        shift.singleTap()
        shift.doubleTap()
        repeat(3) { shift.onCharacterTyped() }
        assertEquals(ShiftMode.CAPS, shift.mode.value)
        assertTrue(shift.mode.value.isShifted)
        shift.singleTap()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        assertFalse(shift.mode.value.isShifted)
    }

    @Test
    fun `two single toggles cancel without typing`() {
        val shift = KeyboardShiftState()
        shift.singleTap()
        shift.singleTap()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        assertFalse(shift.mode.value.isShifted)
    }

    @Test
    fun `setting shifted enters single shift and can be turned off by the shift key`() {
        val shift = KeyboardShiftState()
        shift.setShifted(true)
        assertEquals(ShiftMode.SINGLE, shift.mode.value)
        assertTrue(shift.mode.value.isShifted)
        shift.singleTap()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        assertFalse(shift.mode.value.isShifted)
    }

    @Test
    fun `setting shifted does not replace caps lock with a single character`() {
        val shift = KeyboardShiftState()
        shift.doubleTap()
        shift.setShifted(true)
        shift.onCharacterTyped()
        assertEquals(ShiftMode.CAPS, shift.mode.value)
        shift.setShifted(false)
        assertEquals(ShiftMode.OFF, shift.mode.value)
    }

    @Test
    fun `new session reset clears single shift and caps lock`() {
        val shift = KeyboardShiftState()
        shift.singleTap()
        shift.reset()
        assertFalse(shift.mode.value.isShifted)
        shift.doubleTap()
        shift.reset()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        assertFalse(shift.mode.value.isShifted)
    }

    @Test
    fun `rapid typing uses current shift even before the key label recomposes`() {
        val shift = KeyboardShiftState()
        shift.singleTap()
        assertEquals("Q", shift.mode.value.applyToKey("q"))
        shift.onCharacterTyped()
        assertEquals("q", shift.mode.value.applyToKey("Q"))
    }

    @Test
    fun `caps lock preserves editor actions symbols and custom text`() {
        for (key in listOf("delete", "space", "enter", "mode_change", "，", "拼音a")) {
            assertEquals(key, ShiftMode.CAPS.applyToKey(key))
        }
    }
    @Test fun `holding shift persists across letters and release restores lowercase`() {
        val shift = KeyboardShiftState()
        shift.beginHold()
        repeat(3) {
            assertEquals("A", shift.mode.value.applyToKey("a"))
            shift.onCharacterTyped()
        }
        assertTrue(shift.endHold())
        assertEquals(ShiftMode.OFF, shift.mode.value)
    }
    @Test fun `unused hold can cancel or become a single tap and caps survives temporary hold`() {
        val shift = KeyboardShiftState()
        shift.beginHold(); assertFalse(shift.endHold())
        assertEquals(ShiftMode.OFF, shift.mode.value)
        shift.singleTap(); shift.beginHold(); shift.onCharacterTyped(); shift.endHold()
        assertEquals(ShiftMode.OFF, shift.mode.value)
        shift.doubleTap(); shift.beginHold(); shift.onCharacterTyped(); shift.endHold()
        assertEquals(ShiftMode.CAPS, shift.mode.value)
        shift.reset(); shift.endHold(); assertEquals(ShiftMode.OFF, shift.mode.value)
    }

}
