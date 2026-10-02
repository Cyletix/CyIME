package com.kingzcheung.xime.model

import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.speech.SpeechModelCatalog

enum class DeviceModelTier { LIGHT, STANDARD, ENHANCED }

/** A model recommendation only. It says nothing about GPU, animations or storage speed. */
data class DeviceModelProfile(val tier: DeviceModelTier, val predictionModel: String, val voiceModel: String)

object DeviceModelProfiles {
    const val GIB = 1024L * 1024 * 1024
    const val SMALL = "predictive-text-small"
    const val BASE = "predictive-text-base"
    const val STANDARD_CPU_KHZ = 1_800_000L
    const val ENHANCED_CPU_KHZ = 2_200_000L

    /** Initial heuristic, not a benchmark or an ongoing policy override. */
    fun choose(device: DeviceCapabilities): DeviceModelProfile {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val frequency = device.maxCpuKHz?.takeIf { it > 0 }
        val tier = when {
            device.lowRam != false || memory == null || cores == null -> DeviceModelTier.LIGHT
            memory < 4 * GIB || cores < 4 || frequency != null && frequency < STANDARD_CPU_KHZ -> DeviceModelTier.LIGHT
            memory >= 6 * GIB && (frequency == null || frequency >= ENHANCED_CPU_KHZ) -> DeviceModelTier.ENHANCED
            else -> DeviceModelTier.STANDARD
        }
        return DeviceModelProfile(tier, if (tier == DeviceModelTier.LIGHT) SMALL else BASE,
            if (tier == DeviceModelTier.ENHANCED) SpeechModelCatalog.ZIPFORMER_TWO_PASS else SpeechModelCatalog.ZIPFORMER)
    }
}
