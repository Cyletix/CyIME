package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LetterRowGeometryTest {
    private val standard = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { row -> row.map { it.toString() } }

    @Test fun threeLetterRowsKeepEqualCapsAcrossPhoneTabletAndFloatingWidths() {
        for (width in listOf(220f, 280f, 360f, 600f, 936f, 1493f)) {
            for (allowShrink in listOf(false, true)) {
                for (explicitWidth in listOf(false, true)) {
                    val policy = if (explicitWidth) KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE)
                        else KeyVisualPolicy.Qwerty
                    val metrics = keyVisualMetrics(policy, width, 300f, 10f, allowShrink = allowShrink)
                        .withAppliedGutter(width, 10f, 8f, applyGutter = true)
                    val geometry = standardLetterRowGeometry(standard, metrics.cellWidthDp, false, false)!!
                    val bodyWidth = width - metrics.gutterX * 2f
                    val topCapWidth = bodyWidth / 10f - metrics.insetX!! * 2f
                    val middleCapWidth = (bodyWidth - geometry.middleRowInsetDp * 2f) / 9f - metrics.insetX * 2f
                    val bottomCapWidth = bodyWidth / (7f + geometry.outerKeyWeight * 2f) - metrics.insetX * 2f
                    assertEquals("middle row at $width dp", topCapWidth, middleCapWidth, 0.001f)
                    assertEquals("bottom row at $width dp", topCapWidth, bottomCapWidth, 0.001f)
                }
            }
        }
    }

    @Test fun floatingUsesRenderedEightDpInsetInsteadOfMetricFourDpInset() {
        val raw = keyVisualMetrics(KeyVisualPolicy.Qwerty, 280f, 200f, 10f, allowShrink = true)
        assertEquals(4f, raw.gutterX, 0.001f)
        val actual = raw.withAppliedGutter(280f, 10f, 8f, applyGutter = true)
        assertEquals(26.4f, actual.cellWidthDp, 0.001f)
        assertEquals(13.2f, standardLetterRowGeometry(standard, actual.cellWidthDp, false, false)!!.middleRowInsetDp, 0.001f)
        assertEquals(raw.insetX, actual.insetX)
        assertEquals(raw.insetY, actual.insetY)
    }

    @Test fun customMergedAndSplitLayoutsKeepTheirOwnRowRules() {
        assertNull(standardLetterRowGeometry(standard, 60f, true, false))
        assertNull(standardLetterRowGeometry(standard, 60f, false, true))
        assertNull(standardLetterRowGeometry(listOf(listOf("qw", "er", "ty", "ui", "op")), 60f, false, false))
        assertNull(standardLetterRowGeometry(standard.map { it.map { key -> key + key } }, 60f, false, false))
        assertNull(standardLetterRowGeometry(standard.dropLast(1), 60f, false, false))
        assertNull(standardLetterRowGeometry(standard, 0f, false, false))
        // The geometry follows rows, so another language using the same 26-key shape also gets equal caps.
        assertNotNull(standardLetterRowGeometry(standard.map { it.reversed() }, 60f, false, false))
    }
}
