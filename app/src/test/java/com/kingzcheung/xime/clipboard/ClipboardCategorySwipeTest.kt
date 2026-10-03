package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.tan

class ClipboardCategorySwipeTest {
    @Test fun bothHorizontalConesIncludeThirtyDegrees() {
        for (x in listOf(-100f, 100f)) for (sign in listOf(-1, 1)) {
            val swipe = ClipboardCategorySwipe(10f, 48f)
            swipe.move(x, 100f * tan(Math.toRadians(30.0)).toFloat() * sign)
            assertTrue(swipe.horizontal)
            assertEquals(if (x < 0) 1 else -1, swipe.pageDelta())
        }
    }

    @Test fun DiagonalAndVerticalMovementNeverClaimsTheList() {
        for (angle in listOf(31.0, 45.0, 60.0, 75.0)) for (sign in listOf(-1, 1)) {
            val swipe = ClipboardCategorySwipe(10f, 48f)
            swipe.move(100f * sign, 100f * tan(Math.toRadians(angle)).toFloat())
            assertTrue(swipe.vertical)
            assertEquals(0, swipe.pageDelta())
        }
        val vertical = ClipboardCategorySwipe(10f, 48f)
        vertical.move(0f, -100f)
        assertTrue(vertical.vertical)
    }

    @Test fun pagingNeedsMoreTravelThanOrdinaryScrollAndDoesNotUseVelocity() {
        val swipe = ClipboardCategorySwipe(10f, 48f)
        swipe.move(-5f, 0f)
        assertFalse(swipe.horizontal)
        swipe.move(-12f, 0f)
        assertTrue(swipe.horizontal)
        assertEquals(0, swipe.pageDelta())
        swipe.move(-47f, 0f)
        assertEquals(0, swipe.pageDelta())
        swipe.move(-48f, 0f)
        assertEquals(1, swipe.pageDelta())
        swipe.move(-500f, 0f)
        assertEquals(1, swipe.pageDelta())
    }

    @Test fun verticalIntentCannotBecomePagingLaterInTheSameGesture() {
        val swipe = ClipboardCategorySwipe(10f, 48f)
        swipe.move(4f, 12f)
        swipe.move(-200f, 12f)
        assertTrue(swipe.vertical)
        assertEquals(0, swipe.pageDelta())
    }

    @Test fun returningNearStartOrEndingOutsideTheConeCancelsThePageChange() {
        val swipe = ClipboardCategorySwipe(10f, 48f)
        swipe.move(-100f, 0f)
        swipe.move(-20f, 0f)
        assertEquals(0, swipe.pageDelta())
        swipe.move(-100f, 90f)
        assertEquals(0, swipe.pageDelta())
    }

    @Test fun categoryOrderIsStableAndEdgesDoNotWrap() {
        assertEquals(ClipboardFilter.ALL, ClipboardFilter.ALL.afterSwipe(-1))
        assertEquals(ClipboardFilter.TEXT, ClipboardFilter.ALL.afterSwipe(1))
        assertEquals(ClipboardFilter.IMAGE, ClipboardFilter.TEXT.afterSwipe(1))
        assertEquals(ClipboardFilter.LINK, ClipboardFilter.IMAGE.afterSwipe(1))
        assertEquals(ClipboardFilter.IMAGE, ClipboardFilter.LINK.afterSwipe(-1))
        assertEquals(ClipboardFilter.EMAIL, ClipboardFilter.EMAIL.afterSwipe(1))
    }
}
