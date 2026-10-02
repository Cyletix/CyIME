package com.kingzcheung.xime.runtime.adaptation

/** Pure, replayable policy. No Android calls, preferences writes, model IO or input contents. */
object RuntimePolicyResolver {
    const val OBSERVATION_WINDOW_MS = 10_000L
    const val RECOVERY_MS = 30_000L
    private const val MAX_SAMPLE_GAP_MS = 15_000L

    data class History(
        val atMs: Long,
        val proposal: RuntimePolicyProposal,
        val pendingPressureSinceMs: Long? = null,
        val healthySinceMs: Long? = null,
        val lastRecoveryMs: Long? = null,
    )

    fun resolve(device: DeviceCapabilities, signals: RuntimeSignals, requests: RuntimeRequests,
        previous: History? = null): History {
        val now = signals.sampledAtMs
        // Duplicate/out-of-order events cannot count as additional windows or recoveries.
        if (previous != null && now < previous.atMs) return previous
        val old = previous
        val continuous = old != null && now - old.atMs <= MAX_SAMPLE_GAP_MS
        val urgent = buildSet {
            if (signals.memoryLow == true) add(PolicyReason.LOW_MEMORY)
            if (signals.thermal >= ThermalState.SEVERE) add(PolicyReason.THERMAL_PRESSURE)
        }
        val mild = buildSet {
            if (signals.powerSave == true) add(PolicyReason.POWER_SAVE)
            if (signals.batteryLow == true && signals.charging == false) add(PolicyReason.LOW_BATTERY)
            if (signals.thermal == ThermalState.MODERATE) add(PolicyReason.THERMAL_PRESSURE)
        }
        val pendingSince = if (mild.isNotEmpty()) {
            if (continuous) old.pendingPressureSinceMs ?: now else now
        } else null
        val pressure = urgent + if (pendingSince != null && now - pendingSince >= OBSERVATION_WINDOW_MS) mild else emptySet()
        val knownHealthy = signals.powerSave == false && signals.memoryLow == false &&
            (signals.batteryLow == false || signals.charging == true) &&
            (signals.thermal in setOf(ThermalState.NONE, ThermalState.LIGHT) ||
                device.sdk < 29 && signals.thermal == ThermalState.UNKNOWN)
        val healthySince = if (knownHealthy) {
            if (continuous) old.healthySinceMs ?: now else now
        } else null
        val conservative = device.memoryEstimate in setOf(MemoryEstimate.UNKNOWN, MemoryEstimate.CONSERVATIVE)
        val unknown = signals.powerSave == null || signals.memoryLow == null ||
            (signals.batteryLow == null && signals.charging != true) ||
            (signals.batteryLow == true && signals.charging == null) || signals.thermal == ThermalState.UNKNOWN
        val restrictions = pressure + when {
            conservative -> setOf(if (device.memoryEstimate == MemoryEstimate.UNKNOWN) PolicyReason.UNKNOWN_SIGNALS else PolicyReason.CONSERVATIVE_MEMORY)
            unknown -> setOf(PolicyReason.UNKNOWN_SIGNALS)
            else -> emptySet()
        }
        val animationTarget = when {
            signals.animationsEnabled == false -> AnimationLevel.OFF
            pressure.isNotEmpty() -> minOf(requests.animation.value, AnimationLevel.REDUCED)
            conservative || signals.animationsEnabled == null || unknown -> minOf(requests.animation.value, AnimationLevel.STANDARD)
            else -> requests.animation.value
        }
        var animation = RuntimeRecommendation(requests.animation, animationTarget, when {
            requests.animation.value == AnimationLevel.OFF -> setOf(PolicyReason.NOT_REQUESTED)
            signals.animationsEnabled == false -> setOf(PolicyReason.SYSTEM_ANIMATIONS_OFF)
            restrictions.isNotEmpty() -> restrictions
            signals.animationsEnabled == null -> setOf(PolicyReason.UNKNOWN_SIGNALS)
            else -> setOf(PolicyReason.REQUESTED)
        })
        var glow = RuntimeRecommendation(requests.keyGlow,
            requests.keyGlow.value && restrictions.isEmpty() && signals.animationsEnabled == true, when {
                !requests.keyGlow.value -> setOf(PolicyReason.NOT_REQUESTED)
                signals.animationsEnabled == false -> setOf(PolicyReason.SYSTEM_ANIMATIONS_OFF)
                restrictions.isNotEmpty() -> restrictions
                signals.animationsEnabled == null -> setOf(PolicyReason.UNKNOWN_SIGNALS)
                else -> setOf(PolicyReason.REQUESTED)
            })
        var neural = RuntimeRecommendation(requests.neuralPrediction,
            requests.neuralPrediction.value && restrictions.isEmpty(), when {
                !requests.neuralPrediction.value -> setOf(PolicyReason.NOT_REQUESTED)
                restrictions.isNotEmpty() -> restrictions
                else -> setOf(PolicyReason.RESOURCE_CHECK_REQUIRED)
            })
        // No latency/quality/memory qualification has been collected in P1. Even 8+ GiB is
        // only permission to measure, not evidence that a second recognizer is affordable.
        val correction = RuntimeRecommendation(requests.voiceCorrection, false, when {
            !requests.voiceCorrection.value -> setOf(PolicyReason.NOT_REQUESTED)
            restrictions.isNotEmpty() -> restrictions
            else -> setOf(PolicyReason.MEASUREMENT_REQUIRED)
        })
        var lastRecovery = old?.lastRecoveryMs
        if (old != null) {
            var mayRecover = healthySince != null && now - healthySince >= RECOVERY_MS &&
                (lastRecovery == null || now - lastRecovery >= RECOVERY_MS)
            // At most one feature recovers per interval. A missing interval is not a healthy sample.
            if (animation.proposed > old.proposal.animation.proposed) {
                if (mayRecover) { lastRecovery = now; mayRecover = false }
                else animation = animation.copy(proposed = old.proposal.animation.proposed,
                    reasons = setOf(PolicyReason.RECOVERY_HOLD))
            }
            if (neural.proposed && !old.proposal.neuralPrediction.proposed) {
                if (mayRecover) { lastRecovery = now; mayRecover = false }
                else neural = neural.copy(proposed = false, reasons = setOf(PolicyReason.RECOVERY_HOLD))
            }
            // Decorative glow recovers after motion and prediction, never enabling either one.
            if (glow.proposed && !old.proposal.keyGlow.proposed) {
                if (mayRecover) lastRecovery = now
                else glow = glow.copy(proposed = false, reasons = setOf(PolicyReason.RECOVERY_HOLD))
            }
        }
        return History(now, RuntimePolicyProposal(animation, neural, correction, glow), pendingSince, healthySince, lastRecovery)
    }
}
