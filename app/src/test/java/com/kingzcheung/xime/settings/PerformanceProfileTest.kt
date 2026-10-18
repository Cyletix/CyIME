package com.kingzcheung.xime.settings

import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.runtime.adaptation.PerformanceTiers
import org.junit.Assert.*
import org.junit.Test

class PerformanceProfileTest {
    private val gib = DeviceCapabilities.GIB
    private val device = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * gib, 8, 3_360_000)

    @Test fun `tiers come from one shared threshold set`() {
        assertEquals(PerformanceTier.HIGH, PerformanceTiers.classify(device))
        assertEquals(PerformanceTier.MEDIUM, PerformanceTiers.classify(device.copy(totalMemoryBytes = 6 * gib)))
        assertEquals(PerformanceTier.LOW, PerformanceTiers.classify(device.copy(totalMemoryBytes = 3 * gib)))
        assertEquals(PerformanceTier.LOW, PerformanceTiers.classify(device.copy(lowRam = null)))
    }

    @Test fun `a preset is shown only when every value matches, otherwise custom`() {
        PerformanceProfile.presets.forEach { (tier, values) -> assertEquals(tier, PerformanceProfile.matching(values)) }
        val high = PerformanceProfile.presets.getValue(PerformanceTier.HIGH)
        assertNull(PerformanceProfile.matching(high.copy(keyGlow = false)))
        assertNull(PerformanceProfile.matching(high.copy(speechRefinerIdleMinutes = 5)))
    }

    @Test fun `presets release the second pass before the whole engine`() {
        PerformanceProfile.presets.values.forEach {
            val engine = it.speechEngineIdleMinutes
            assertTrue(it.toString(), engine == 0 || it.speechRefinerIdleMinutes in 1..engine)
        }
    }
}
