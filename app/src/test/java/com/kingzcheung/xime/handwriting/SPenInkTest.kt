package com.kingzcheung.xime.handwriting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SPenInkTest {
    @Test fun penInkIsSPenBlueOnDarkPaperAndDeepenedButStillBlueOnLightPaper() {
        assertEquals(SPenInkBlue, handwritingInk(SPenInkBlue, Color.Black))
        val light = handwritingInk(SPenInkBlue, Color.White)
        assertTrue(light.luminance() < SPenInkBlue.luminance())
        assertTrue(light.blue > light.red && light.blue > light.green)
    }

    @Test fun onlyStrokesFromThePenAreMarkedAsPen() {
        assertFalse(StrokePoint(1f, 2f, 0).pen)
        assertTrue(StrokePoint(1f, 2f, 0, pen = true).pen)
    }
}
