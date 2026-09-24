package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyGlowTimingTest {
    @Test fun capShrinksHoldsAndRecoversInThreeEqualPhases() {
        for ((ms, scale) in listOf(0 to 1f, 25 to .94f, 50 to .88f, 75 to .88f, 100 to .88f, 125 to .94f, 150 to 1f, 500 to 1f)) {
            assertEquals("$ms ms", scale, keyGlowScale(ms / 500f), .0001f)
        }
    }
    @Test fun squaresHoldThenShrinkWithSlowingFade() {
        assertEquals(1f, keyGlowRemaining(0f), 0f)
        assertEquals(1f, keyGlowRemaining(.2f), 0f)
        assertEquals(0f, keyGlowRemaining(1f), 0f)
        val frames = (100..500 step 10).map { keyGlowRemaining(it / 500f) }
        assertTrue(frames.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(frames.first() - frames[1] > frames[frames.lastIndex - 1] - frames.last())
    }
}
