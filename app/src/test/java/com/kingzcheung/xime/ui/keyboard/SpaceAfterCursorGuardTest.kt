package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpaceAfterCursorGuardTest {
    @Before fun reset() = SpaceAfterCursorGuard.resetForTest()

    @Test fun `space types normally when the caret was never moved`() {
        assertFalse(SpaceAfterCursorGuard.blocksSpace(10_000L))
    }

    @Test fun `a tap starting within 200 ms after the caret move is ignored`() {
        SpaceAfterCursorGuard.mark(10_000L)
        assertTrue(SpaceAfterCursorGuard.blocksSpace(10_000L))
        assertTrue(SpaceAfterCursorGuard.blocksSpace(10_199L))
    }

    @Test fun `a tap from 200 ms on types a space again`() {
        SpaceAfterCursorGuard.mark(10_000L)
        assertFalse(SpaceAfterCursorGuard.blocksSpace(10_200L))
        assertFalse(SpaceAfterCursorGuard.blocksSpace(15_000L))
    }

    @Test fun `the window restarts from the latest caret step`() {
        SpaceAfterCursorGuard.mark(10_000L)
        SpaceAfterCursorGuard.mark(10_500L)
        assertTrue(SpaceAfterCursorGuard.blocksSpace(10_650L))
    }
}
