package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorStepAccumulatorTest {
    @Test
    fun `small moves accumulate without requiring one large swipe`() {
        val cursor = CursorStepAccumulator(10f)
        assertEquals(0, cursor.move(4f))
        assertEquals(0, cursor.move(4f))
        assertEquals(1, cursor.move(4f))
        assertEquals(1, cursor.move(8f))
    }

    @Test
    fun `reversing direction does not first consume the old remainder`() {
        val cursor = CursorStepAccumulator(10f)
        assertEquals(1, cursor.move(18f))
        assertEquals(-1, cursor.move(-10f))
        assertEquals(1, cursor.move(10f))
    }

    @Test
    fun `reverse movement accumulates from its own turning point`() {
        val cursor = CursorStepAccumulator(10f)
        assertEquals(1, cursor.move(18f))
        assertEquals(0, cursor.move(-4f))
        assertEquals(0, cursor.move(-4f))
        assertEquals(-1, cursor.move(-2f))
    }

    @Test
    fun `configured distance controls sensitivity in both directions`() {
        assertEquals(3, CursorStepAccumulator(6f).move(18f))
        assertEquals(-3, CursorStepAccumulator(6f).move(-18f))
        val slower = CursorStepAccumulator(24f)
        assertEquals(0, slower.move(18f))
        assertEquals(1, slower.move(6f))
    }

    @Test
    fun `new hold starts without previous gesture remainder`() {
        val previous = CursorStepAccumulator(10f)
        assertEquals(0, previous.move(9f))
        assertEquals(0, CursorStepAccumulator(10f).move(1f))
    }
}
