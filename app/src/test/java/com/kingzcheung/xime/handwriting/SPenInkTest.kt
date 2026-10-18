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

    @Test fun harderPenPressureDrawsAThickerLineWithinBounds() {
        assertTrue(sPenWidthFactor(1f) > sPenWidthFactor(.5f))
        assertTrue(sPenWidthFactor(.5f) > sPenWidthFactor(0f))
        assertEquals(sPenWidthFactor(1f), sPenWidthFactor(3f))
        assertTrue(sPenWidthFactor(0f) > 0f)
        assertEquals(1f, StrokePoint(0f, 0f, 0).pressure)
    }

    @Test fun penSoundIsSilentWhenStillAndFullAtWritingSpeed() {
        assertEquals(0f, SPenSounds.targetVolume(0f, 2f))
        assertEquals(.5f, SPenSounds.targetVolume(1f, 2f))
        assertEquals(1f, SPenSounds.targetVolume(10f, 2f))
    }
}
