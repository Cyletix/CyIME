package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryPrefetchTest {
    private val ok = PrefetchPolicy.Conditions()
    private val mb = 1L shl 20
    private val gb = 1L shl 30
    private fun files(resident: Float) = listOf(
        PrefetchPolicy.FileState("prism", 1 * mb, resident),
        PrefetchPolicy.FileState("table", 55 * mb, resident),
        PrefetchPolicy.FileState("model", 203 * mb, resident),
    )

    @Test fun coldFilesAreReadMostValuableFirstWhenMemoryAllows() {
        val policy = PrefetchPolicy()
        assertTrue(policy.shouldCheck(0, ok))
        assertEquals(listOf("prism", "table", "model"), policy.plan(0, files(0f), 4 * gb, 1 * gb))
    }

    @Test fun shortOfMemoryReadsOnlyWhatFitsAboveTheReserve() {
        val policy = PrefetchPolicy()
        // 100 MB above the reserve: prism and table fit, the model does not.
        assertEquals(listOf("prism", "table"), policy.plan(0, files(0f), 1 * gb + 100 * mb, 1 * gb))
        // Nothing above the reserve: read nothing.
        assertEquals(emptyList<String>(), PrefetchPolicy().plan(0, files(0f), 1 * gb, 1 * gb))
    }

    @Test fun warmFilesAreLeftAlone() {
        assertEquals(emptyList<String>(), PrefetchPolicy().plan(0, files(0.95f), 4 * gb, 1 * gb))
    }

    @Test fun checksAreSpacedButTypingFaultsBypassTheInterval() {
        val policy = PrefetchPolicy()
        policy.plan(0, files(0f), 4 * gb, 1 * gb)
        assertFalse(policy.shouldCheck(5_000, ok))
        assertTrue(policy.shouldCheck(PrefetchPolicy.MIN_CHECK_GAP_MS, ok))
        // Within the interval a cold check reads nothing ...
        assertEquals(emptyList<String>(), policy.plan(20_000, files(0.6f), 4 * gb, 1 * gb))
        // ... unless the last typing session waited on the disk.
        policy.onSessionEnd(PrefetchPolicy.SESSION_FAULTS)
        assertTrue(policy.shouldCheck(21_000, ok))
        assertEquals(3, policy.plan(21_000, files(0.6f), 4 * gb, 1 * gb).size)
    }

    @Test fun refilledPagesLostAgainSoonWidenTheIntervalOnceAndStableChecksResetIt() {
        val policy = PrefetchPolicy()
        policy.plan(0, files(0f), 4 * gb, 1 * gb)
        policy.plan(60_000, files(0.2f), 4 * gb, 1 * gb)
        assertEquals(2 * policy.minIntervalMs, policy.intervalMs)
        policy.plan(70_000, files(0.2f), 4 * gb, 1 * gb)
        assertEquals(2 * policy.minIntervalMs, policy.intervalMs)
        repeat(PrefetchPolicy.STABLE_CHECKS_TO_RESET) { policy.plan(200_000L + it * 20_000, files(1f), 4 * gb, 1 * gb) }
        assertEquals(policy.minIntervalMs, policy.intervalMs)
    }

    @Test fun memoryPressureHoldsOffAndTheIntervalNeverPassesTheMaximum() {
        val policy = PrefetchPolicy()
        policy.onMemoryPressure(0)
        assertFalse(policy.shouldCheck(PrefetchPolicy.PRESSURE_HOLD_MS - 1, ok))
        assertTrue(policy.shouldCheck(PrefetchPolicy.PRESSURE_HOLD_MS, ok))
        repeat(20) { policy.onMemoryPressure(0) }
        assertEquals(policy.maxIntervalMs, policy.intervalMs)
    }

    @Test fun powerSaveLowMemoryLowRamAndHeatBlock() {
        val policy = PrefetchPolicy()
        assertFalse(policy.shouldCheck(0, PrefetchPolicy.Conditions(powerSave = true)))
        assertFalse(policy.shouldCheck(0, PrefetchPolicy.Conditions(lowMemory = true)))
        assertFalse(policy.shouldCheck(0, PrefetchPolicy.Conditions(lowRamDevice = true)))
        assertFalse(policy.shouldCheck(0, PrefetchPolicy.Conditions(thermalSevere = true)))
    }
}
