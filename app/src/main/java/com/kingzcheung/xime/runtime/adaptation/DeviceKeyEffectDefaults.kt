package com.kingzcheung.xime.runtime.adaptation

data class KeyEffectDefaults(val animation: Boolean, val glow: Boolean)

/** First-run key effects follow the shared [PerformanceTiers]: motion from medium, glow only on high. */
object DeviceKeyEffectDefaults {
    fun choose(device: DeviceCapabilities,
               thresholds: PerformanceTiers.CpuThresholds? = PerformanceTiers.cpuThresholds): KeyEffectDefaults {
        val tier = PerformanceTiers.classify(device, thresholds)
        return KeyEffectDefaults(animation = tier != PerformanceTier.LOW, glow = tier == PerformanceTier.HIGH)
    }
}
