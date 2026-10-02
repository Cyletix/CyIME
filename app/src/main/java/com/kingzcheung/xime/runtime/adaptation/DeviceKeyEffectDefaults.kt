package com.kingzcheung.xime.runtime.adaptation

data class KeyEffectDefaults(val animation: Boolean, val glow: Boolean)

/** First-run heuristics for key effects only, not model tiers or a GPU benchmark. */
object DeviceKeyEffectDefaults {
    fun choose(device: DeviceCapabilities): KeyEffectDefaults {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val frequency = device.maxCpuKHz?.takeIf { it > 0 }
        val animation = device.lowRam == false && memory != null && memory >= 4 * DeviceCapabilities.GIB &&
            cores != null && cores >= 4 && (frequency == null || frequency >= 1_800_000L)
        val glow = animation && (memory ?: 0) >= 6 * DeviceCapabilities.GIB &&
            (frequency == null || frequency >= 2_200_000L)
        return KeyEffectDefaults(animation, glow)
    }
}
