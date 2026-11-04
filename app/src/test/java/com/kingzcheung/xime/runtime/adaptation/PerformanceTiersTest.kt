package com.kingzcheung.xime.runtime.adaptation

import org.junit.Assert.*
import org.junit.Test

class PerformanceTiersTest {
    private val gib = DeviceCapabilities.GIB
    private val device = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * gib, 8, 2_800_000)
    // 测试用的分档线，不是实测值。
    private val lines = PerformanceTiers.CpuThresholds(highSingleNs = 20_000_000, highDualNs = 25_000_000,
        lowSingleNs = 60_000_000, lowDualNs = 70_000_000)
    private val fast = CpuMeasurement(15_000_000, 18_000_000)
    private val middling = CpuMeasurement(40_000_000, 45_000_000)
    private val slow = CpuMeasurement(80_000_000, 90_000_000)

    private fun verdict(d: DeviceCapabilities) = PerformanceTiers.explain(d, lines)

    @Test fun `big memory and a high clock are not enough for high without a measurement`() {
        // 11-04 用户反馈：很差的手机也评成高配（8 GB + 2.2 GHz 以上）。
        listOf(device, device.copy(totalMemoryBytes = 11 * gib, maxCpuKHz = 2_840_000), device.copy(totalMemoryBytes = 16 * gib))
            .forEach { assertEquals(it.toString(), PerformanceTiers.Verdict(PerformanceTier.MEDIUM,
                PerformanceTiers.Limit.UNMEASURED, null), verdict(it)) }
        // 主频低仍说明慢。
        assertEquals(PerformanceTier.LOW, verdict(device.copy(maxCpuKHz = 1_700_000)).tier)
    }

    @Test fun `the measured cpu decides and memory only caps`() {
        assertEquals(PerformanceTiers.Verdict(PerformanceTier.HIGH, PerformanceTiers.Limit.CPU, PerformanceTier.HIGH),
            verdict(device.copy(cpu = fast)))
        assertEquals(PerformanceTiers.Verdict(PerformanceTier.MEDIUM, PerformanceTiers.Limit.MEMORY, PerformanceTier.HIGH),
            verdict(device.copy(totalMemoryBytes = 6 * gib, cpu = fast)))
        assertEquals(PerformanceTier.MEDIUM, verdict(device.copy(cpu = middling)).tier)
        // 内存大补不了 CPU 慢；主频不再参与。
        assertEquals(PerformanceTiers.Verdict(PerformanceTier.LOW, PerformanceTiers.Limit.CPU, PerformanceTier.LOW),
            verdict(device.copy(totalMemoryBytes = 12 * gib, maxCpuKHz = 3_000_000, cpu = slow)))
        // 测过就不看主频：主频读数低但实测快照样高配。
        assertEquals(PerformanceTier.HIGH, verdict(device.copy(maxCpuKHz = 1_500_000, cpu = fast)).tier)
    }

    @Test fun `single and dual thread each decide and the lower one wins`() {
        assertEquals(PerformanceTier.MEDIUM, PerformanceTiers.cpuTier(CpuMeasurement(15_000_000, 40_000_000), lines))
        assertEquals(PerformanceTier.LOW, PerformanceTiers.cpuTier(CpuMeasurement(15_000_000, 70_000_000), lines))
        assertEquals(PerformanceTier.HIGH, PerformanceTiers.cpuTier(CpuMeasurement(20_000_000, 25_000_000), lines))
    }

    @Test fun `no thresholds or an old benchmark version count as unmeasured`() {
        assertNull(PerformanceTiers.cpuTier(fast, null))
        assertNull(PerformanceTiers.cpuTier(fast.copy(version = CpuBenchmark.VERSION - 1), lines))
        assertEquals(PerformanceTiers.Limit.UNMEASURED, PerformanceTiers.explain(device.copy(cpu = fast), null).limit)
        assertEquals(PerformanceTier.MEDIUM, PerformanceTiers.classify(device.copy(cpu = fast), null))
    }

    @Test fun `small or unknown hardware stays low whatever the cpu says`() {
        assertEquals(PerformanceTiers.Verdict(PerformanceTier.LOW, PerformanceTiers.Limit.MEMORY, PerformanceTier.HIGH),
            verdict(device.copy(totalMemoryBytes = 3 * gib, cpu = fast)))
        listOf(device.copy(lowRam = true), device.copy(lowRam = null), device.copy(totalMemoryBytes = null),
            device.copy(processors = null)).forEach {
            assertEquals(it.toString(), PerformanceTiers.Limit.UNKNOWN, verdict(it.copy(cpu = fast)).limit)
            assertEquals(it.toString(), PerformanceTier.LOW, verdict(it.copy(cpu = fast)).tier)
        }
        assertEquals(PerformanceTier.LOW, verdict(device.copy(processors = 2, cpu = fast)).tier)
    }

    @Test(expected = IllegalArgumentException::class) fun `a high line slower than the low line is rejected`() {
        PerformanceTiers.CpuThresholds(60_000_000, 25_000_000, 20_000_000, 70_000_000)
    }
}
