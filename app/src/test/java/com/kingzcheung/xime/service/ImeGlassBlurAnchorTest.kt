package com.kingzcheung.xime.service

import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeGlassBlurAnchorTest {
    @Test fun `blur follows the keyboard when the IME window grows at the top without a new report`() {
        // Status bar hidden: the window gains 96 px at the top, the keyboard stays put on screen,
        // so it moves 96 px down inside the window. The stale bounds must move with it.
        assertEquals(IntOffset(0, 96), blurAnchorShift(IntOffset(0, 1200), IntOffset(0, 1296)))
        // Window shrinks again: blur moves back up.
        assertEquals(IntOffset(0, -96), blurAnchorShift(IntOffset(0, 1296), IntOffset(0, 1200)))
    }

    @Test fun `unchanged anchor or unknown origin keeps bounds as reported`() {
        assertEquals(IntOffset.Zero, blurAnchorShift(IntOffset(0, 1200), IntOffset(0, 1200)))
        assertEquals(IntOffset.Zero, blurAnchorShift(null, IntOffset(0, 1200)))
        assertEquals(IntOffset.Zero, blurAnchorShift(IntOffset(0, 1200), null))
    }
}
