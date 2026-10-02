package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyboardDisplayStateTest {
    @Test fun `without a physical keyboard the normal screen keyboard remains available`() {
        val state = HardwareKeyboardDisplayState()
        assertFalse(state.connected)
        assertFalse(state.compact)
    }

    @Test fun `a new physical keyboard connection starts with the toolbar`() {
        val state = HardwareKeyboardDisplayState().withConnection(true)
        assertTrue(state.connected)
        assertTrue(state.compact)
        assertFalse(state.screenKeyboardRequested)
    }

    @Test fun `explicit expansion survives repeated focus and configuration notifications`() {
        val expanded = HardwareKeyboardDisplayState().withConnection(true).showOnScreen()
        assertTrue(expanded.connected)
        assertTrue(expanded.screenKeyboardRequested)
        assertFalse(expanded.compact)
        assertEquals(expanded, expanded.withConnection(true).withConnection(true))
    }

    @Test fun `collapse returns to the toolbar without disconnecting physical input`() {
        val collapsed = HardwareKeyboardDisplayState().withConnection(true).showOnScreen().collapseToToolbar()
        assertTrue(collapsed.connected)
        assertTrue(collapsed.compact)
        assertFalse(collapsed.screenKeyboardRequested)
    }

    @Test fun `disconnecting clears expansion while retaining the normal keyboard mode`() {
        val disconnected = HardwareKeyboardDisplayState().withConnection(true).showOnScreen().withConnection(false)
        assertEquals(HardwareKeyboardDisplayState(), disconnected)
        assertFalse(disconnected.compact)
    }

    @Test fun `reconnecting after disconnect starts with the toolbar again`() {
        val reconnected = HardwareKeyboardDisplayState().withConnection(true).showOnScreen()
            .withConnection(false).withConnection(true)
        assertTrue(reconnected.compact)
        assertFalse(reconnected.screenKeyboardRequested)
    }

    @Test fun `toolbar actions cannot change display mode while disconnected`() {
        val disconnected = HardwareKeyboardDisplayState()
        assertEquals(disconnected, disconnected.showOnScreen())
        assertEquals(disconnected, disconnected.collapseToToolbar())
    }

    @Test fun `repeated explicit actions are stable`() {
        val connected = HardwareKeyboardDisplayState(connected = true)
        assertEquals(connected.showOnScreen(), connected.showOnScreen().showOnScreen())
        assertEquals(connected, connected.collapseToToolbar().collapseToToolbar())
    }
}
