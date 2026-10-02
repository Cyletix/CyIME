package com.kingzcheung.xime.runtime.adaptation

import org.junit.Assert.*
import org.junit.Test

class DeviceKeyEffectDefaultsTest {
    private val gib = DeviceCapabilities.GIB
    private val device = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * gib, 8, 2_400_000)

    @Test fun `motion and glow have different first run thresholds`() {
        val cases = listOf(
            device.copy(totalMemoryBytes = 4 * gib - 1) to KeyEffectDefaults(false, false),
            device.copy(totalMemoryBytes = 4 * gib, processors = 4, maxCpuKHz = 1_800_000) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 6 * gib - 1) to KeyEffectDefaults(true, false),
            device.copy(totalMemoryBytes = 6 * gib, maxCpuKHz = 2_200_000) to KeyEffectDefaults(true, true),
            device.copy(maxCpuKHz = 2_199_999) to KeyEffectDefaults(true, false),
            device.copy(maxCpuKHz = 1_799_999) to KeyEffectDefaults(false, false),
        )
        cases.forEach { (hardware, expected) -> assertEquals(hardware.toString(), expected, DeviceKeyEffectDefaults.choose(hardware)) }
    }

    @Test fun `unknown frequency falls back but unknown essentials never enable effects`() {
        assertEquals(KeyEffectDefaults(true, true), DeviceKeyEffectDefaults.choose(device.copy(maxCpuKHz = null)))
        assertEquals(KeyEffectDefaults(true, false), DeviceKeyEffectDefaults.choose(device.copy(totalMemoryBytes = 4 * gib, maxCpuKHz = 0)))
        listOf(device.copy(lowRam = true), device.copy(lowRam = null), device.copy(totalMemoryBytes = null),
            device.copy(totalMemoryBytes = 0), device.copy(processors = null), device.copy(processors = 3))
            .forEach { assertEquals(KeyEffectDefaults(false, false), DeviceKeyEffectDefaults.choose(it)) }
    }
}
