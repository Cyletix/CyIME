package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.runtime.adaptation.CpuBenchmarkRunner
import com.kingzcheung.xime.runtime.adaptation.CpuMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub #268: a quick retest with the same numbers looked like nothing happened. */
class CpuLineTest {
    private val measured = CpuMeasurement(singleNs = 12_000_000, dualNs = 15_000_000)

    @Test fun retestIsVisible() {
        assertEquals("测试中…", cpuLine(measured, null, running = true))
        assertTrue(cpuLine(measured, null, running = false, retested = true).endsWith("刚重测"))
        assertFalse(cpuLine(measured, null, running = false).contains("刚重测"))
    }

    @Test fun skippedReasonWinsOverRetested() {
        val line = cpuLine(measured, CpuBenchmarkRunner.Skip.KEYBOARD, running = false, retested = true)
        assertTrue(line.endsWith("没重测：键盘开着"))
    }
}
