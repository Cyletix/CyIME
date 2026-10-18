package com.kingzcheung.xime.runtime.adaptation

data class KeyEffectDefaults(val animation: Boolean, val glow: Boolean)

/** First-run key effects follow the shared [PerformanceTiers]: motion from medium, glow only on high. */
object DeviceKeyEffectDefaults {
    fun choose(device: DeviceCapabilities): KeyEffectDefaults {
        val tier = PerformanceTiers.classify(device)
        return KeyEffectDefaults(animation = tier != PerformanceTier.LOW, glow = tier == PerformanceTier.HIGH)
    }
}
