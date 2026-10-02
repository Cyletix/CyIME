package com.kingzcheung.xime.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardInputSessionTest {
    @Test fun `standalone keyboard initializes even the default zero session`() {
        val session = KeyboardInputSession()
        assertFalse(session.initialized)
        assertEquals(KeyboardInputSession.Change(true, true), session.update(0, 0))
        assertTrue(session.initialized)
    }

    @Test fun `screen keyboard remount does not reset the editor or its active overlay`() {
        val session = KeyboardInputSession()
        session.update(12, 3)
        repeat(3) {
            assertEquals(KeyboardInputSession.Change(false, false), session.update(12, 3))
        }
    }

    @Test fun `session initialized while compact is not initialized again on first expansion`() {
        val session = KeyboardInputSession()
        assertTrue(session.update(24, 0).newSession)
        assertEquals(KeyboardInputSession.Change(false, false), session.update(24, 0))
    }

    @Test fun `new editor resets even without a reset signal change`() {
        val session = KeyboardInputSession()
        session.update(24, 5)
        assertEquals(KeyboardInputSession.Change(true, true), session.update(25, 5))
    }

    @Test fun `explicit reset keeps the current page and only resets the controller once`() {
        val session = KeyboardInputSession()
        session.update(24, 5)
        assertEquals(KeyboardInputSession.Change(false, true), session.update(24, 6))
        assertEquals(KeyboardInputSession.Change(false, false), session.update(24, 6))
    }

    @Test fun `ending a session and changing both counters still produces one reset`() {
        val session = KeyboardInputSession()
        session.update(24, 5)
        assertEquals(KeyboardInputSession.Change(true, true), session.update(25, 6))
        assertEquals(KeyboardInputSession.Change(false, false), session.update(25, 6))
    }
}
