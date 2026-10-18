package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class SymbolGridGeometryTest {
    @Test fun tabletUsesAvailableHeightInsteadOfFifteenTinySquares() {
        val grid = symbolGridGeometry(760f, 240f)
        assertEquals(9, grid.columns)
        assertEquals(80f, grid.rowHeightDp, 0f)
        assertEquals(240f, grid.rowHeightDp * 3f, 0f)
        // The 32 common symbols occupy the viewport and continue below it.
        assertTrue(kotlin.math.ceil(32f / grid.columns) * grid.rowHeightDp >= 240f)
    }
    @Test fun wideAndNarrowViewportsRetainUsableKeysAndScrollWhenNecessary() {
        for (width in listOf(180f, 360f, 760f, 1400f)) for (height in listOf(60f, 180f, 288f, 480f)) {
            val grid = symbolGridGeometry(width, height)
            assertTrue(grid.columns in 4..12)
            assertTrue(grid.rowHeightDp in 40f..96f)
            assertTrue(width / grid.columns >= 40f)
        }
    }
    @Test fun portraitAndLandscapeUseIdenticalRulesForIdenticalPanelBounds() {
        assertTrue(symbolGridGeometry(700f, 240f).columns < symbolGridGeometry(700f, 120f).columns)
        assertTrue(symbolGridGeometry(700f, 240f).rowHeightDp > symbolGridGeometry(700f, 120f).rowHeightDp)
    }

    @Test fun emojiFitsWholeRowsInMinimumViewportAndShowsMoreOnLargerPanels() {
        for (height in listOf(84f, 104f, 128f, 148f, 220f, 400f)) {
            val grid = emojiGridGeometry(372f, height)
            val rows = ((height + 2f) / (grid.rowHeightDp + 2f)).toInt()
            assertTrue(grid.columns >= 8)
            assertTrue("Readable cells at height $height", grid.rowHeightDp in 40f..52f)
            assertEquals("No clipped last row at height $height", height,
                rows * grid.rowHeightDp + (rows - 1) * 2f, .01f)
        }
        assertTrue(emojiGridGeometry(760f, 148f).columns > emojiGridGeometry(372f, 148f).columns)
    }
}
