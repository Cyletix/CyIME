package com.kingzcheung.xime.runtime.adaptation

import org.junit.Assert.*
import org.junit.Test

class CpuBenchmarkTest {
    @Test fun `warms up then takes the median of alternating single and dual passes`() {
        val calls = mutableListOf<Int>()
        val singles = ArrayDeque(listOf(99L, 99L, 30L, 10L, 20L, 50L, 40L))
        val duals = ArrayDeque(listOf(11L, 15L, 13L, 12L, 14L))
        val run = CpuBenchmark.measure(pass = { threads ->
            calls += threads
            if (threads == 1) CpuBenchmark.Pass(singles.removeFirst(), 0b0100) else CpuBenchmark.Pass(duals.removeFirst(), 0b1100)
        }, clock = { 0L })!!
        // 两遍空跑（不计），之后一份、两份交替。
        assertEquals(listOf(1, 1, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2), calls)
        assertEquals(CpuMeasurement(30, 13), run.measurement)
        assertEquals("2", CpuBenchmark.cpuList(run.singleCpus))
        assertEquals("2-3", CpuBenchmark.cpuList(run.dualCpus))
    }

    @Test fun `a slow device stops after three passes each once the budget is used`() {
        var now = 0L
        val run = CpuBenchmark.measure(pass = { now += 1_000_000_000L; CpuBenchmark.Pass(1_000_000_000L, 1) }, clock = { now })!!
        assertEquals(3, run.single.size)
        assertEquals(3, run.dual.size)
    }

    @Test fun `the keyboard appearing mid run throws the result away`() {
        var passes = 0
        assertNull(CpuBenchmark.measure(shouldStop = { passes >= 4 }, pass = { passes++; CpuBenchmark.Pass(1, 1) }, clock = { 0L }))
        assertEquals(4, passes)
    }

    @Test fun `a failed native pass gives no result`() {
        assertNull(CpuBenchmark.measure(pass = { CpuBenchmark.Pass(-1, 0) }, clock = { 0L }))
    }

    @Test fun `median and cpu lists`() {
        assertEquals(3L, CpuBenchmark.median(listOf(5, 1, 3)))
        assertEquals(3L, CpuBenchmark.median(listOf(4, 1, 2, 9)))
        assertEquals("0-3,7", CpuBenchmark.cpuList(0b1000_1111))
        assertEquals("", CpuBenchmark.cpuList(0))
        assertEquals("63", CpuBenchmark.cpuList(Long.MIN_VALUE))
    }
}
