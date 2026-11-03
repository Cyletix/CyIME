package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingCursiveSimplifyTest {

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 30) =
        (0 until n).map { i -> val t = i / (n - 1f); (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t) }

    /** 几笔连成一笔：来回折 [turns] 次的锯齿。 */
    private fun zigzag(turns: Int, perLeg: Int = 12): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        for (k in 0 until turns) {
            val y0 = if (k % 2 == 0) 0f else 100f
            out += line(k * 20f, y0, (k + 1) * 20f, 100f - y0, perLeg).let { if (out.isEmpty()) it else it.drop(1) }
        }
        return out
    }

    @Test fun plainStrokesKeepEightPoints() {
        val strokes = listOf(line(0f, 0f, 100f, 0f), line(50f, -50f, 50f, 50f), line(0f, 0f, 100f, 100f, n = 5))
        assertEquals(HandwritingInference.simplifyStrokes(strokes), HandwritingInference.simplifyStrokesCursive(strokes))
    }

    @Test fun cursiveStrokeKeepsItsCorners() {
        val stroke = zigzag(9)
        assertEquals(10, HandwritingInference.cornerCount(stroke, 0.05f * 180f))
        val out = HandwritingInference.simplifyStrokesCursive(listOf(stroke, line(0f, 120f, 180f, 120f)))
        assertEquals(24, out[0].size)  // 10 个拐点 → 每 4 个 8 点 → 24
        assertEquals(8, out[1].size)
    }

    @Test fun cappedAtFortyPointsPerStroke() {
        val out = HandwritingInference.simplifyStrokesCursive(listOf(zigzag(40, perLeg = 6)))
        assertEquals(40, out.single().size)
    }

    @Test fun fallsBackWhenModelInputWouldOverflow() {
        val strokes = (0 until 6).map { zigzag(30, perLeg = 6) }
        val out = HandwritingInference.simplifyStrokesCursive(strokes)
        assertEquals(HandwritingInference.simplifyStrokes(strokes), out)
        assertTrue(out.sumOf { it.size } <= 200)
    }
}
