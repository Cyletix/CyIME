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

    @Test
    fun `the same setting moves the same finger distance on tablet and phone`() {
        // SM-X800 at its 300 dpi override, and the S23 Ultra at FHD+ 450 dpi (1080 px over 2.874 in).
        val phoneStep = 10f * cursorUnitPx(KeyboardPhysicalMetrics.from(2.8125f, 375.8f)!!, 2.8125f)
        assertEquals(10f * 2.8125f, phoneStep, 0.001f) // the phone keeps its old feel
        val tabletStep = 10f * cursorUnitPx(KeyboardPhysicalMetrics.from(1.875f, 266.471f)!!, 1.875f)
        assertEquals(phoneStep * 25.4f / 375.8f, tabletStep * 25.4f / 266.471f, 0.0001f)
        assertEquals(1.90f, cursorDistanceMillimeters(10f), 0.01f)
    }

    @Test
    fun `unknown physical density keeps the logical dp step`() {
        assertEquals(1.875f, cursorUnitPx(null, 1.875f))
    }
}
