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

    /**
     * Initial heuristic, not a benchmark or an ongoing policy override. [bundled] lists the models inside an offline
     * edition: one that ships SenseVoice without Paraformer starts on SenseVoice instead of downloading 226 MB first.
     */
    fun choose(device: DeviceCapabilities, bundled: Set<String> = emptySet()): DeviceModelProfile {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val frequency = device.maxCpuKHz?.takeIf { it > 0 }
        val tier = when {
            device.lowRam != false || memory == null || cores == null -> DeviceModelTier.LIGHT
            memory < 4 * GIB || cores < 4 || frequency != null && frequency < STANDARD_CPU_KHZ -> DeviceModelTier.LIGHT
            memory >= 6 * GIB && (frequency == null || frequency >= ENHANCED_CPU_KHZ) -> DeviceModelTier.ENHANCED
            else -> DeviceModelTier.STANDARD
        }
        val voice = if (SpeechModelCatalog.SENSEVOICE in bundled && SpeechModelCatalog.PARAFORMER !in bundled)
            SpeechModelCatalog.SENSEVOICE else SpeechModelCatalog.PARAFORMER
        return DeviceModelProfile(tier, if (tier == DeviceModelTier.LIGHT) SMALL else BASE, voice)
    }
}
