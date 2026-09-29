package com.kingzcheung.xime.keyboard

import org.junit.Assert.*
import org.junit.Test

class RepeatInputTest {
    @Test fun holdHasOneOutstandingRepeatAndReleaseInvalidatesIt() {
        val hold = RepeatInput()
        assertTrue(hold.acquire())
        repeat(100) { assertFalse(hold.acquire()) }
        hold.completed()
        assertTrue(hold.acquire())
        hold.stop()
        assertFalse(hold.isActive)
        hold.completed()
        assertFalse(hold.acquire())
    }
    @Test fun tapAndNewHoldDoNotInheritPreviousHold() {
        val old = RepeatInput()
        old.dispatch { assertSame(old, RepeatInput.current.get()) }
        assertNull(RepeatInput.current.get())
        old.stop()
        val next = RepeatInput()
        assertTrue(next.acquire())
        assertFalse(old.acquire())
    }
}
