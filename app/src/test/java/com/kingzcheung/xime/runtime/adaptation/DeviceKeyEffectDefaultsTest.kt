package com.kingzcheung.xime.runtime.adaptation

import org.junit.Assert.*
import org.junit.Test

class DeviceKeyEffectDefaultsTest {
    private val gib = DeviceCapabilities.GIB
    private val device = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * gib, 8, 2_400_000)
    // 测试用的分档线，不是实测值。
    private val lines = PerformanceTiers.CpuThresholds(highSingleNs = 20_000_000, highDualNs = 25_000_000,
        lowSingleNs = 60_000_000, lowDualNs = 70_000_000)
    private val fast = CpuMeasurement(15_000_000, 18_000_000)

    @Test fun `motion and glow have different first run thresholds`() {
        val cases = listOf(
            device.copy(totalMemoryBytes = 4 * gib - 1) to KeyEffectDefaults(false, false),
            device.copy(totalMemoryBytes = 4 * gib, processors = 4, maxCpuKHz = 1_800_000) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 6 * gib - 1) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 6 * gib, maxCpuKHz = 2_200_000) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 8 * gib - 1, maxCpuKHz = 2_200_000, cpu = fast) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 8 * gib, maxCpuKHz = 2_200_000, cpu = fast) to KeyEffectDefaults(true, true),
            device.copy(maxCpuKHz = 1_799_999) to KeyEffectDefaults(false, false),
        )
        cases.forEach { (hardware, expected) -> assertEquals(hardware.toString(), expected, DeviceKeyEffectDefaults.choose(hardware, lines)) }
    }

    @Test fun `high clock and big memory alone never turn glow on`() {
        // 11-04：8 GB、2.2 GHz 以上的差手机原来也开炫光。没实测就最多中配。
        assertEquals(KeyEffectDefaults(true, false), DeviceKeyEffectDefaults.choose(device.copy(maxCpuKHz = 3_200_000), lines))
        assertEquals(KeyEffectDefaults(true, false), DeviceKeyEffectDefaults.choose(device.copy(totalMemoryBytes = 16 * gib), lines))
        // 实测慢：内存再大、主频再高也是低配。
        assertEquals(KeyEffectDefaults(false, false), DeviceKeyEffectDefaults.choose(
            device.copy(totalMemoryBytes = 12 * gib, maxCpuKHz = 2_400_000, cpu = CpuMeasurement(80_000_000, 90_000_000)), lines))
    }

    @Test fun `unknown frequency falls back but unknown essentials never enable effects`() {
        assertEquals(KeyEffectDefaults(true, false), DeviceKeyEffectDefaults.choose(device.copy(maxCpuKHz = null), lines))
        assertEquals(KeyEffectDefaults(true, false), DeviceKeyEffectDefaults.choose(device.copy(totalMemoryBytes = 4 * gib, maxCpuKHz = 0), lines))
        listOf(device.copy(lowRam = true), device.copy(lowRam = null), device.copy(totalMemoryBytes = null),
            device.copy(totalMemoryBytes = 0), device.copy(processors = null), device.copy(processors = 3))
            .forEach { assertEquals(KeyEffectDefaults(false, false), DeviceKeyEffectDefaults.choose(it.copy(cpu = fast), lines)) }
    }
}
