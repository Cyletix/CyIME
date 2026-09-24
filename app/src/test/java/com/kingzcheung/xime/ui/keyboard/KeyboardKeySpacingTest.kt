package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardKeySpacingTest {
    @Test fun oneGapTracksTheNormalKeysShortEdgeOnEveryScreen() {
        for ((width, height) in listOf(28f to 48f, 40f to 68f, 90f to 68f, 96f to 120f, 200f to 90f)) {
            val gap = 4f * keyboardKeySpacingScale(width, height).value
            assertEquals(0.08f, gap / minOf(width, height), 0.00001f)
            assertEquals(gap * 2, 4f * keyboardKeySpacingScale(width * 2, height * 2).value, 0.00001f)
        }
    }
    @Test fun wideningAShortKeyDoesNotCreateHugeHorizontalGaps() {
        assertEquals(keyboardKeySpacingScale(100f, 60f), keyboardKeySpacingScale(250f, 60f))
    }
    @Test fun smallFloatingKeysAlsoShrinkTheirGap() {
        assertEquals(0.4f, keyboardKeySpacingScale(20f, 40f).value, 0.00001f)
    }
    @Test fun invalidSizesRemainFinite() {
        for (size in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(size, 70f))
            assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(40f, size))
        }
    }
}
