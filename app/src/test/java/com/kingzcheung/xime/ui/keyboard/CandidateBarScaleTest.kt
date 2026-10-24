package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateBarScaleTest {
    @Test fun phoneDefaultsKeepTheOriginalBar() {
        // 880dp 高手机的默认 35%，以及更矮的手机和横屏。
        for ((width, height) in listOf(411f to 308f, 360f to 280f, 882f to 220f, 411f to 228f)) {
            assertEquals(1f, candidateBarScale(width, height), 0f)
        }
    }

    @Test fun barGrowsAtHalfTheKeyRate() {
        // 行高比手机默认大 40%：候选栏大 20%。
        val height = CANDIDATE_BAR_BASE_HEIGHT_DP + 4f * 66f * 1.4f
        assertEquals(1.2f, candidateBarScale(824f, height), 0.0001f)
        assertEquals(1.1f, candidateBarScale(824f, CANDIDATE_BAR_BASE_HEIGHT_DP + 4f * 66f * 1.2f), 0.0001f)
    }

    @Test fun growthStopsAtAQuarter() {
        assertEquals(1.25f, candidateBarScale(1318f, 700f), 0f)
        assertEquals(1.25f, candidateBarScale(824f, 461f), 0f)
    }

    @Test fun narrowFloatingKeyboardsAreLimitedByWidth() {
        assertEquals(1.1f, candidateBarScale(360f * 1.2f, 600f), 0.0001f)
        assertEquals(1f, candidateBarScale(300f, 600f), 0f)
    }

    @Test fun invalidSizesStayAtBase() {
        assertEquals(1f, candidateBarScale(0f, 400f), 0f)
        assertEquals(1f, candidateBarScale(Float.NaN, 400f), 0f)
    }
}
