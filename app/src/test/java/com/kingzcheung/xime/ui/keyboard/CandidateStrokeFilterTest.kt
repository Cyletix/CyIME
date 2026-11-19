package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStrokeFilterTest {
    @Test fun filtersByFirstCharacterAndPreservesAlternateCodes() {
        val table = CandidateStrokeTable(mapOf("王" to listOf("hhsh"), "重" to listOf("phsh", "shsh")))
        assertTrue(table.matches("王国", "hhs"))
        assertTrue(table.matches("重庆", "sh"))
        assertFalse(table.matches("王国", "hsp"))
        assertFalse(table.matches("未知", "h"))
    }
    @Test fun shortLeftDotMatchesBothDotAndAmbiguousShortFallingStroke() {
        val table = CandidateStrokeTable(mapOf("雷" to listOf("hnzsnnnnszhsh"), "某" to listOf("hpzs")))
        val shortLeft = listOf(Offset(40f, 0f), Offset(26f, 18f))
        assertEquals('N', classifyCandidateStroke(shortLeft))
        assertTrue(table.matches("雷", "hN"))
        assertTrue(table.matches("某", "hN"))
        assertFalse(table.matches("某", "hn"))
        assertEquals("丶", candidateStrokeLabel("N"))
    }

    @Test fun shortDiagonalDotsAreIndependentOfDensityAndLongLeftStrokeRemainsPie() {
        val dot = listOf(Offset(0f, 0f), Offset(12f, 16f))
        assertEquals('n', classifyCandidateStroke(dot))
        assertEquals('n', classifyCandidateStroke(dot.map { it * 3f }, density = 3f))
        assertEquals('p', classifyCandidateStroke(listOf(Offset(60f, 0f), Offset(0f, 70f))))
    }
    @Test fun longerSteepDotsRemainDistinctFromMostlyVerticalStrokes() {
        val rightDot = listOf(Offset(0f, 0f), Offset(14f, 48f))
        val leftDot = listOf(Offset(20f, 0f), Offset(6f, 48f))
        val vertical = listOf(Offset(0f, 0f), Offset(5f, 48f))
        assertEquals('n', classifyCandidateStroke(rightDot))
        assertEquals('n', classifyCandidateStroke(listOf(Offset(0f, 0f), Offset(4f, 20f))))
        assertEquals('N', classifyCandidateStroke(leftDot))
        assertEquals('s', classifyCandidateStroke(vertical))
        assertEquals('n', classifyCandidateStroke(rightDot.map { it * 3f }, density = 3f))
        val table = CandidateStrokeTable(mapOf("雷" to listOf("hnzsnnnnszhsh"), "中" to listOf("szhsh")))
        assertTrue(table.matches("雷", "hN"))
        assertFalse(table.matches("中", "N"))
    }
    @Test fun deletingByDragClearsOnlyOnceEvenWhenMovingBackAndForth() {
        var clearCount = 0
        val drag = strokeFilterDeleteDrag { clearCount++ }
        drag.move(0)
        drag.move(1)
        drag.move(3)
        drag.move(0)
        drag.move(-2)
        drag.finish()
        assertEquals(1, clearCount)
    }
    @Test fun recognizesFiveStrokeDirections() {
        fun stroke(vararg p: Pair<Float, Float>) = classifyCandidateStroke(p.map { Offset(it.first, it.second) })
        assertEquals('h', stroke(0f to 0f, 80f to 2f))
        assertEquals('s', stroke(0f to 0f, 1f to 80f))
        assertEquals('p', stroke(60f to 0f, 0f to 70f))
        assertEquals('n', stroke(0f to 0f, 60f to 70f))
        assertEquals('z', stroke(0f to 0f, 80f to 0f, 80f to 80f))
    }
}
