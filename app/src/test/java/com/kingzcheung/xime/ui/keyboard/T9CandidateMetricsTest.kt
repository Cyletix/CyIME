package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class T9CandidateMetricsTest {
    @Test fun rowHeightTracksPanelInsteadOfChangingWithCandidateCount() {
        val phone = t9CandidateMetrics(54f, 168f, 1f, 1f, 1f)
        val tablet = t9CandidateMetrics(130f, 300f, 1f, 1f, 1.75f)
        assertEquals(42f, phone.rowHeightDp, 0.01f)
        assertEquals(75f, tablet.rowHeightDp, 0.01f)
        assertTrue(tablet.fontSizeSp > phone.fontSizeSp)
        assertTrue(tablet.fontSizeSp >= 24f)
    }
    @Test fun shortPanelsScrollInsteadOfCrushingOptions() {
        val small = t9CandidateMetrics(50f, 90f, 1f, 1f, 1f)
        assertEquals(32f, small.rowHeightDp, 0.01f)
    }
    @Test fun largeSystemTextStillFitsTheLongestSyllable() {
        val metrics = t9CandidateMetrics(54f, 168f, 2f, 1.5f, 1f)
        assertTrue(metrics.fontSizeSp > 0f)
        assertTrue(metrics.fontSizeSp * 2 * 1.4f <= metrics.rowHeightDp - 4f)
    }
}
