package com.kingzcheung.xime.ui.common

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class CompactActionMenuPlacementTest {
    private fun position(width: Int, height: Int, anchor: Rect, occupied: List<Rect>,
        menu: IntSize = IntSize(110, 196), side: Boolean = true) =
        compactMenuBounds(IntSize(width, height), anchor, occupied, menu, 4f, 6f, 52f, side)

    @Test fun widePanelUsesAnEmptyGapInsteadOfCoveringEitherRecord() {
        val first = Rect(6f, 40f, 160f, 110f)
        val second = Rect(166f, 40f, 320f, 180f)
        val menu = position(800, 360, first, listOf(first, second))
        assertFalse(menu.overlaps(first))
        assertFalse(menu.overlaps(second))
        assertEquals(110f, menu.width, 0f)
        assertEquals(196f, menu.height, 0f)
    }

    @Test fun rightEdgeAnchorKeepsMenuInsidePanelAndOffTheRecord() {
        val card = Rect(624f, 40f, 794f, 145f)
        val menu = position(800, 360, card, listOf(card))
        assertFalse(menu.overlaps(card))
        assertTrue(menu.left >= 4f && menu.right <= 796f && menu.top >= 4f && menu.bottom <= 356f)
    }

    @Test fun narrowSingleColumnUsesAScrollableGapBelowTheActualRecord() {
        val card = Rect(6f, 40f, 234f, 110f)
        val menu = position(240, 240, card, listOf(card))
        assertFalse(menu.overlaps(card))
        assertTrue(menu.top >= card.bottom + 6f)
        assertTrue(menu.height in 52f..195f)
        assertTrue(menu.right <= 236f)
    }

    @Test fun bottomRecordCanUseSpaceAboveWithoutMovingTheRecord() {
        val card = Rect(6f, 175f, 234f, 236f)
        val menu = position(240, 240, card, listOf(card))
        assertFalse(menu.overlaps(card))
        assertTrue(menu.bottom <= card.top - 6f)
    }

    @Test fun toolbarMoreButtonAnchorsBelowAndDoesNotStretchToPanelWidth() {
        val button = Rect(766f, 4f, 794f, 32f)
        val menu = position(800, 360, button, emptyList(), IntSize(164, 196), side = false)
        assertTrue(menu.top >= button.bottom + 6f)
        assertEquals(button.right, menu.right, 0f)
        assertEquals(164f, menu.width, 0f)
    }

    @Test fun impossibleSpaceStillBoundsOnlyTheCompactSurface() {
        val card = Rect(0f, 0f, 200f, 100f)
        val menu = position(200, 100, card, listOf(card), IntSize(500, 500))
        assertTrue(menu.left >= 4f && menu.top >= 4f)
        assertTrue(menu.right <= 196f && menu.bottom <= 96f)
    }
}
