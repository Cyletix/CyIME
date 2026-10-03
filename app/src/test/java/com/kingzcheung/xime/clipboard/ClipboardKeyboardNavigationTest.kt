package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardKeyboardNavigationTest {
    @Test fun earlyKeysAreDeliveredInOrderAndCloseDropsPendingInput() {
        var closed = 0
        val state = ClipboardKeyboardNavigation { closed++ }
        val events = mutableListOf<ClipboardNavigation>()
        state.open()
        state.dispatch(ClipboardNavigation.RIGHT)
        state.dispatch(ClipboardNavigation.CONFIRM)
        state.dispatch(ClipboardNavigation.DOWN)
        state.attach { action -> events += action; if (action == ClipboardNavigation.CONFIRM) state.close() }
        assertEquals(listOf(ClipboardNavigation.RIGHT, ClipboardNavigation.CONFIRM), events)
        assertEquals(1, closed)
        assertFalse(state.active)
        state.open(); state.dispatch(ClipboardNavigation.LEFT); state.reset()
        state.attach { events += it }
        assertEquals(2, events.size)
    }
    @Test fun cancelClosesEvenBeforeBoardMountsAndClosedBoardDoesNotHandleKeys() {
        var closed = 0
        val state = ClipboardKeyboardNavigation { closed++ }
        state.open(); state.dispatch(ClipboardNavigation.CANCEL)
        state.dispatch(ClipboardNavigation.CONFIRM)
        assertEquals(1, closed)
        assertFalse(state.active)
    }
    @Test fun directionalSelectionUsesStaggeredCellGeometry() {
        val keys = listOf("a", "b", "c", "d")
        val cells = listOf(ClipboardCell("a", 0, 0, 100, 100), ClipboardCell("b", 110, 0, 100, 180),
            ClipboardCell("c", 0, 110, 100, 100), ClipboardCell("d", 110, 190, 100, 100))
        assertEquals("b", nextClipboardKey(keys, "a", ClipboardNavigation.RIGHT, 2, cells))
        assertEquals("c", nextClipboardKey(keys, "a", ClipboardNavigation.DOWN, 2, cells))
        assertEquals("a", nextClipboardKey(keys, "c", ClipboardNavigation.UP, 2, cells))
        assertEquals("c", nextClipboardKey(keys, "d", ClipboardNavigation.LEFT, 2, cells))
    }
    @Test fun navigationHandlesEmptyDeletedAndOffscreenRecordsWithoutWrappingPastEnds() {
        val keys = List(8) { "$it" }
        assertNull(nextClipboardKey(emptyList(), null, ClipboardNavigation.RIGHT, 2, emptyList()))
        assertEquals("0", nextClipboardKey(keys, "removed", ClipboardNavigation.LEFT, 2, emptyList()))
        assertEquals("6", nextClipboardKey(keys, "3", ClipboardNavigation.DOWN, 3, emptyList()))
        assertEquals("7", nextClipboardKey(keys, "7", ClipboardNavigation.DOWN, 3, emptyList()))
        assertEquals("0", nextClipboardKey(keys, "0", ClipboardNavigation.UP, 3, emptyList()))
    }
}
