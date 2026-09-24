package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyGlowTimingTest {
    @Test fun continuousCapPulseHasNoHoldAndSettlesGently() {
        assertEquals(1f, keyGlowScale(0f), .0001f)
        assertEquals(.88f, keyGlowScale(37.5f / 500f), .0001f)
        val recovery = (40..150 step 5).map { keyGlowScale(it / 500f) }
        assertTrue(recovery.zipWithNext().all { (a, b) -> b > a })
        assertTrue("no 50–100 ms plateau", keyGlowScale(.2f) > keyGlowScale(.1f))
        assertTrue("return velocity tends to zero", keyGlowScale(.3f) - keyGlowScale(.29f) < .0001f)
        assertEquals(1f, keyGlowScale(.3f), 0f)
        assertEquals(1f, keyGlowScale(1f), 0f)
    }
    @Test fun lightMovesImmediatelyWithStrongDecelerationAndIndependentFade() {
        val movement = (0..500 step 10).map { keyGlowTravel(it / 500f) }
        val opacity = (0..500 step 10).map { keyGlowRemaining(it / 500f) }
        assertTrue(movement.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(opacity.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(movement[1] > movement[40] - movement[39])
        assertTrue(keyGlowRemaining(.1f) < 1f)
        assertTrue("glow outlives cap recovery", keyGlowRemaining(.3f) > .4f)
        assertEquals(0f, keyGlowRemaining(1f), 0f)
        assertEquals(1f, keyGlowTravel(1f), 0f)
    }
}
