package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class InputLatencyWindowTest {
    @Test fun reportsPersistentSub100MillisecondDelayWithoutPerKeyReports() {
        val window = InputLatencyWindow()
        repeat(63) { assertNull(window.record(3, 17)) }
        assertEquals(InputLatencyWindow.Summary(64, 20, 20, 20, 3, 17), window.record(3, 17))
        repeat(63) { assertNull(window.record(0, 1)) }
        assertEquals(InputLatencyWindow.Summary(64, 1, 1, 1, 0, 1), window.record(0, 1))
    }

    @Test fun totalPercentilesUseEachActionsTotalInsteadOfAddingUnrelatedPercentiles() {
        val window = InputLatencyWindow()
        repeat(32) { window.record(10, 0) }
        repeat(31) { window.record(0, 10) }
        val summary = requireNotNull(window.record(0, 10))
        assertEquals(10L, summary.totalP95Ms)
        assertEquals(10L, summary.waitP95Ms)
        assertEquals(10L, summary.executionP95Ms)
    }

    @Test fun isolatedStallIsRetainedAsMaximum() {
        val window = InputLatencyWindow()
        repeat(63) { window.record(0, 2) }
        val summary = requireNotNull(window.record(4, 500))
        assertEquals(2L, summary.totalMedianMs)
        assertEquals(2L, summary.totalP95Ms)
        assertEquals(504L, summary.totalMaxMs)
    }
}
