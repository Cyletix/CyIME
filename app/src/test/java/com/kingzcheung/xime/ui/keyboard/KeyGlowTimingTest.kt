package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyGlowTimingTest {
    @Test fun capFollowsMeasuredVideoFramesAndReturnsWithoutASnap() {
        assertEquals(1f, keyGlowScale(0f), .0001f)
        assertEquals(.667f, keyGlowScale(83f / 500f), .0001f)
        assertEquals(.867f, keyGlowScale(117f / 500f), .0001f)
        assertEquals(1.052f, keyGlowScale(167f / 500f), .0001f)
        assertEquals(1f, keyGlowScale(235f / 500f), .0001f)
        assertEquals(1f, keyGlowScale(1f), 0f)
        assertTrue("settles with near-zero velocity", keyGlowScale(234f / 500f) - 1f < .0002f)
        val samples = (0..500).map { keyGlowScale(it / 500f) }
        assertTrue(samples.all { it in .66f..1.06f })
        assertTrue("no frame discontinuity", samples.zipWithNext().all { (a,b) -> kotlin.math.abs(a-b) < .01f })
    }
    @Test fun squareFacesShrinkButRemainRecognisableThroughoutTheFade() {
        assertEquals(1f, keyGlowSquareSize(0f), 0f)
        assertEquals(.65f, keyGlowSquareSize(.5f), .0001f)
        assertTrue(keyGlowSquareSize(.8f) > .4f)
        assertTrue((0..500).map { keyGlowSquareSize(it / 500f) }.zipWithNext().all { (a,b) -> b <= a })
    }
    @Test fun lightMovesImmediatelyWithStrongDecelerationAndIndependentFade() {
        val movement = (0..500 step 10).map { keyGlowTravel(it / 500f) }
        val opacity = (0..500 step 10).map { keyGlowRemaining(it / 500f) }
        assertTrue(movement.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(opacity.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(movement[1] > movement[40] - movement[39])
        assertTrue(keyGlowRemaining(.1f) < 1f)
        assertTrue("glow outlives cap recovery", keyGlowRemaining(.5f) > .2f)
        assertEquals(0f, keyGlowRemaining(1f), 0f)
        assertEquals(1f, keyGlowTravel(1f), 0f)
    }
}
