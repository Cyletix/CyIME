package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceCursorShiftTest {
    @After fun reset() = SpaceCursorShift.end()

    @Test fun `held shift selects only while it is held`() {
        SpaceCursorShift.begin()
        assertFalse(SpaceCursorShift.selecting(ShiftMode.OFF))
        assertTrue(SpaceCursorShift.selecting(ShiftMode.HELD))
        assertTrue(SpaceCursorShift.selecting(ShiftMode.HELD))
        assertFalse(SpaceCursorShift.selecting(ShiftMode.OFF))
    }

    @Test fun `one-shot shift keeps selecting for the whole drag after it is spent`() {
        SpaceCursorShift.begin()
        assertTrue(SpaceCursorShift.selecting(ShiftMode.SINGLE))
        assertTrue(SpaceCursorShift.selecting(ShiftMode.OFF))
        SpaceCursorShift.end()
        SpaceCursorShift.begin()
        assertFalse(SpaceCursorShift.selecting(ShiftMode.OFF))
    }

    @Test fun `one-shot shift tapped after the drag started does not latch`() {
        SpaceCursorShift.begin()
        assertFalse(SpaceCursorShift.selecting(ShiftMode.OFF))
        assertFalse(SpaceCursorShift.selecting(ShiftMode.SINGLE))
    }

    @Test fun `caps lock does not select and nothing selects outside a space drag`() {
        SpaceCursorShift.begin()
        assertFalse(SpaceCursorShift.selecting(ShiftMode.CAPS))
        SpaceCursorShift.end()
        assertFalse(SpaceCursorShift.selecting(ShiftMode.HELD))
    }
}
