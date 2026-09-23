package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardKeySpacingTest {
    @Test fun normalPhonesAndSmallFloatingCardsKeepTheirSpacing() {
        for ((width, height) in listOf(360f to 240f, 412f to 280f, 300f to 210f)) {
            assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(width, height))
        }
    }
    @Test fun proportionalBodyGrowthProducesProportionalGaps() {
        assertEquals(KeyboardKeySpacingScale(1f, 1f), keyboardKeySpacingScale(420f, 280f))
        assertEquals(KeyboardKeySpacingScale(1.5f, 1.5f), keyboardKeySpacingScale(630f, 420f))
        assertEquals(KeyboardKeySpacingScale(2f, 2f), keyboardKeySpacingScale(840f, 560f))
    }
    @Test fun widthOnlyGrowthPreservesTheHorizontalGapToKeyWidthRatio() {
        val scale = keyboardKeySpacingScale(1260f, 280f)
        assertEquals(3f, scale.horizontal, 0.0001f)
        assertEquals(1f, scale.vertical, 0.0001f)
        assertEquals(4f / (420f / 10f), (4f * scale.horizontal) / (1260f / 10f), 0.0001f)
    }
    @Test fun unboundedOrInvalidConstraintsDoNotExplodeSpacing() {
        assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(Float.POSITIVE_INFINITY, 280f))
        assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(420f, Float.NaN))
        assertEquals(KeyboardKeySpacingScale(), keyboardKeySpacingScale(0f, 280f))
    }
}
