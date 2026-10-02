package com.kingzcheung.xime.runtime.adaptation

/** Monotonic timestamps; null means unavailable, never a zero-cost/high-performance device. */
data class DeviceCapabilities(
    val sampledAtMs: Long,
    val sdk: Int,
    val abis: List<String>,
    val lowRam: Boolean?,
    val totalMemoryBytes: Long?,
    val processors: Int?,
    /** Hardware maximum, not the current/governor-limited frequency. Unknown when unreadable. */
    val maxCpuKHz: Long? = null,
) {
    val memoryEstimate: MemoryEstimate get() = when {
        lowRam == true || totalMemoryBytes?.let { it in 1 until 4 * GIB } == true -> MemoryEstimate.CONSERVATIVE
        lowRam == null || totalMemoryBytes == null || totalMemoryBytes <= 0 -> MemoryEstimate.UNKNOWN
        totalMemoryBytes < 8 * GIB -> MemoryEstimate.STANDARD
        else -> MemoryEstimate.MEASUREMENT_ELIGIBLE
    }

    companion object { const val GIB = 1024L * 1024 * 1024 }
}

enum class MemoryEstimate { UNKNOWN, CONSERVATIVE, STANDARD, MEASUREMENT_ELIGIBLE }
enum class ThermalState { UNKNOWN, NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

/** Facts from Android system APIs, not model ownership or an app-exclusive memory budget. */
data class RuntimeSignals(
    val sampledAtMs: Long,
    val powerSave: Boolean? = null,
    val batteryLow: Boolean? = null,
    val charging: Boolean? = null,
    val thermal: ThermalState = ThermalState.UNKNOWN,
    val availableMemoryBytes: Long? = null,
    val memoryLow: Boolean? = null,
    val animationsEnabled: Boolean? = null,
    val unavailableSources: Set<String> = emptySet(),
)

/** A saved legacy value may have been written by an installer. Never call it an explicit choice. */
enum class PreferenceOrigin { SAVED_UNATTRIBUTED, CURRENT_DEFAULT }
data class RuntimeRequest<T>(val value: T, val origin: PreferenceOrigin)
/** Keycap motion only. Decorative glow has its own independent recommendation. */
enum class AnimationLevel { OFF, REDUCED, STANDARD }

data class RuntimeRequests(
    val animation: RuntimeRequest<AnimationLevel>,
    val neuralPrediction: RuntimeRequest<Boolean>,
    val voiceCorrection: RuntimeRequest<Boolean>,
    val keyGlow: RuntimeRequest<Boolean>,
)

enum class PolicyReason {
    REQUESTED, NOT_REQUESTED, SYSTEM_ANIMATIONS_OFF, UNKNOWN_SIGNALS, CONSERVATIVE_MEMORY,
    POWER_SAVE, LOW_BATTERY, THERMAL_PRESSURE, LOW_MEMORY, RECOVERY_HOLD,
    RESOURCE_CHECK_REQUIRED, MEASUREMENT_REQUIRED,
}

data class RuntimeRecommendation<T>(
    val requested: RuntimeRequest<T>,
    val proposed: T,
    val reasons: Set<PolicyReason>,
)

/**
 * Observation only. Permission to attempt work is NOT model availability/loading success.
 * This type deliberately cannot be mistaken for an applied EffectiveRuntimePolicy.
 * Resource/language checks and task leases must precede any future execution adapter.
 */
data class RuntimePolicyProposal(
    val animation: RuntimeRecommendation<AnimationLevel>,
    val neuralPrediction: RuntimeRecommendation<Boolean>,
    val voiceCorrection: RuntimeRecommendation<Boolean>,
    val keyGlow: RuntimeRecommendation<Boolean>,
    val speculativePrewarm: Boolean = false,
    val prewarmReason: PolicyReason = PolicyReason.MEASUREMENT_REQUIRED,
)

data class RuntimeObservation(
    val generation: Long,
    val capabilities: DeviceCapabilities,
    val signals: RuntimeSignals,
    val proposal: RuntimePolicyProposal,
)
