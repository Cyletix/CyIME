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

    @Test fun autoHoldsOnlyWhileTheValuesAreStillTheDetectedTier() {
        val high = PerformanceProfile.presets.getValue(PerformanceTier.HIGH)
        assertTrue(PerformanceProfile.isAuto(true, high, PerformanceTier.HIGH))
        // Glow switched off on the 按键效果 page: the page shows 自定义, not 自动.
        assertFalse(PerformanceProfile.isAuto(true, high.copy(keyGlow = false), PerformanceTier.HIGH))
        assertFalse(PerformanceProfile.isAuto(true, PerformanceProfile.presets.getValue(PerformanceTier.LOW), PerformanceTier.HIGH))
        assertFalse(PerformanceProfile.isAuto(false, high, PerformanceTier.HIGH))
    }

    @Test fun firstRunKeyEffectsAreTheSameTierPreset() {
        listOf(device, device.copy(totalMemoryBytes = 6 * gib), device.copy(totalMemoryBytes = 3 * gib)).forEach {
            val preset = PerformanceProfile.presets.getValue(PerformanceTiers.classify(it))
            val effects = com.kingzcheung.xime.runtime.adaptation.DeviceKeyEffectDefaults.choose(it)
            assertEquals(it.toString(), preset.keyAnimation, effects.animation)
            assertEquals(it.toString(), preset.keyGlow, effects.glow)
        }
    }

    @Test fun scoreRunsFromLowToFullAndTiersSitInOrder() {
        val low = PerformanceProfile.presets.getValue(PerformanceTier.LOW)
        val medium = PerformanceProfile.presets.getValue(PerformanceTier.MEDIUM)
        val high = PerformanceProfile.presets.getValue(PerformanceTier.HIGH)
        assertEquals(0, PerformanceProfile.score(low))
        assertEquals(45, PerformanceProfile.score(medium))
        assertEquals(88, PerformanceProfile.score(high))
        assertEquals(100, PerformanceProfile.score(PerformanceSettings(true, true, 0, 0)))
    }

    @Test fun refinerKeptLongerThanTheWholeEngineDoesNotScoreMore() {
        val low = PerformanceProfile.presets.getValue(PerformanceTier.LOW)
        assertEquals(PerformanceProfile.score(low), PerformanceProfile.score(low.copy(speechRefinerIdleMinutes = 0)))
    }

    @Test fun offLevelValuesScoreLikeTheNearestSliderLevel() {
        val medium = PerformanceProfile.presets.getValue(PerformanceTier.MEDIUM)
        assertEquals(PerformanceProfile.idleLevelIndex(30), PerformanceProfile.idleLevelIndex(25))
        assertEquals(PerformanceProfile.score(medium), PerformanceProfile.score(medium.copy(speechEngineIdleMinutes = 25)))
    }
}
