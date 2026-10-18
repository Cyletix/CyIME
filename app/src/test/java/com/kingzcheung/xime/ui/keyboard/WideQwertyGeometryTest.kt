package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WideQwertyGeometryTest {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { it.map(Char::toString) }
    private val target = CALIBRATED_QWERTY_REFERENCE_CELL_DP

    @Test fun tabletCalibrationReachesAnsiLetterCentresAndPreservesEqualCellWidths() {
        val geometry = standardLetterRowGeometry(rows, target, false, false, target)!!
        assertEquals(.25f * target, geometry.middleRowInsetDp, .001f)
        assertEquals(.75f * target, geometry.outerKeyWeight * target, .001f)
        assertEquals(.5f * target, geometry.outerKeyWeight * target - geometry.middleRowInsetDp, .001f)
        assertEquals(target, (10f * target - geometry.middleRowInsetDp - geometry.middleRowEndInsetDp) / 9f, .001f)
        assertEquals(target, 10f * target / (geometry.outerKeyWeight + 7 + geometry.deleteKeyWeight), .001f)
        assertEquals(.75f, geometry.outerKeyWeight, .001f)
        assertEquals(2.25f, geometry.deleteKeyWeight, .001f)
    }

    @Test fun compactCoordinatesStayOriginalAndCompleteCustomDerivativesShareWideFrame() {
        for (cell in listOf(20f, 26.4f, 34.4f, 56f, 68.8f)) {
            val old = standardLetterRowGeometry(rows, cell, false, false)
            assertEquals(old, standardLetterRowGeometry(rows, cell, false, false, target))
        }
        val reversed = rows.map { it.reversed() }
        assertEquals(standardLetterRowGeometry(rows, 150f, false, false, target),
            standardLetterRowGeometry(reversed, 150f, false, false, target))
        assertEquals(standardLetterRowGeometry(rows, target, false, false, target),
            standardLetterRowGeometry(rows, target, true, false, target))
        assertNull(standardLetterRowGeometry(rows, target, false, true, target))
    }

    @Test fun eachInterpolatedRowRetainsTenUnitsAndHasNoPositionJump() {
        var previous = standardLetterRowGeometry(rows, 20f, false, false, target)!!
        for (step in 201..2000) {
            val cell = step / 10f
            val current = standardLetterRowGeometry(rows, cell, false, false, target)!!
            assertEquals(cell, current.middleRowInsetDp + current.middleRowEndInsetDp, .001f)
            assertEquals(3f, current.outerKeyWeight + current.deleteKeyWeight, .0001f)
            assertTrue(current.outerKeyWeight >= .75f && current.deleteKeyWeight >= 1.5f)
            assertTrue(abs(current.middleRowInsetDp - previous.middleRowInsetDp) < .2f)
            assertTrue(abs(current.outerKeyWeight - previous.outerKeyWeight) < .01f)
            previous = current
        }
        // Manual extra-wide widths complete the stagger, but are never narrowed here.
        assertEquals(1f, wideQwertyStagger(200f, target), 0f)
    }

    @Test fun aDensityChangeKeepsTheSamePhysicalRowOffsets() {
        val physical = 15f
        var last: Pair<Float, Float>? = null
        for (density in listOf(1f, 1.875f, 3f)) {
            val metrics = KeyboardPhysicalMetrics.from(density, 266.471f)!!
            val cell = metrics.dpFromMillimeters(physical.toDouble())
            val calibrated = metrics.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
            val geometry = standardLetterRowGeometry(rows, cell, false, false, calibrated)!!
            val values = metrics.millimetersFromDp(geometry.middleRowInsetDp) to
                metrics.millimetersFromDp(geometry.outerKeyWeight * cell)
            last?.let { assertEquals(it.first, values.first, .001f); assertEquals(it.second, values.second, .001f) }
            last = values
        }
    }
}
