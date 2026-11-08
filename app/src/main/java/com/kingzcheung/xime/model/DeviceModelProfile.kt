package com.kingzcheung.xime.model

import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.speech.SpeechModelCatalog

enum class DeviceModelTier { LIGHT, STANDARD, ENHANCED }

/** A model recommendation only. It says nothing about GPU, animations or storage speed. */
data class DeviceModelProfile(val tier: DeviceModelTier, val voiceModel: String)

object DeviceModelProfiles {
    const val GIB = 1024L * 1024 * 1024
    const val STANDARD_CPU_KHZ = 1_800_000L
    const val ENHANCED_CPU_KHZ = 2_200_000L

    /**
     * Initial heuristic, not a benchmark or an ongoing policy override. [bundled] lists the models inside an offline
     * edition. Voice starts on SenseVoice (Chinese 7.4 % CER vs Paraformer alone 15–18 %, 2026-10-07 FLEURS; wrong text
     * is worse than a later one); only an edition that ships Paraformer (elder/friend packs, with SenseVoice) starts on it.
     */
    fun choose(device: DeviceCapabilities, bundled: Set<String> = emptySet(),
               thresholds: com.kingzcheung.xime.runtime.adaptation.PerformanceTiers.CpuThresholds? =
                   com.kingzcheung.xime.runtime.adaptation.PerformanceTiers.cpuThresholds): DeviceModelProfile {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val frequency = device.maxCpuKHz?.takeIf { it > 0 }
        // 测过 CPU 就按实测算力，不再看主频（11-04：主频高的差手机被当成强机器）；没测过沿用主频估计。
        val cpu = com.kingzcheung.xime.runtime.adaptation.PerformanceTiers.cpuTier(device.cpu, thresholds)
        val slow = if (cpu != null) cpu == PerformanceTier.LOW else frequency != null && frequency < STANDARD_CPU_KHZ
        // Until the high line is calibrated, the low line can veto an existing model
        // recommendation, but a quicker measurement alone cannot change it.
        val fast = if (cpu != null && thresholds?.highSingleNs != null) cpu == PerformanceTier.HIGH
            else frequency == null || frequency >= ENHANCED_CPU_KHZ
        val tier = when {
            device.lowRam != false || memory == null || cores == null -> DeviceModelTier.LIGHT
            memory < 4 * GIB || cores < 4 || slow -> DeviceModelTier.LIGHT
            memory >= 6 * GIB && fast -> DeviceModelTier.ENHANCED
            else -> DeviceModelTier.STANDARD
        }
        val voice = if (SpeechModelCatalog.PARAFORMER in bundled) SpeechModelCatalog.PARAFORMER else SpeechModelCatalog.SENSEVOICE
        return DeviceModelProfile(tier, voice)
    }
}
