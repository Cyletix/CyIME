package com.kingzcheung.xime.speech

import kotlin.math.abs
import kotlin.math.max

/** Bounded gain for VAD only. The recognizer always receives the original PCM.
 * The 0.2 peak target and 16x cap recover attenuated speech without normalizing
 * arbitrary near-zero noise to full scale. 0.05 left distant speech over desk/fan rumble
 * (tablet, 2026-10-19: peaks 0.03–0.08, 78–90% of energy below 300 Hz) without a single
 * speech frame; 0.2 opens those while silence, hiss, 60 Hz hum, clicks and fan-only noise
 * still never start recognition (tools/speech_lab replay.py / fan_noise_replay.py). A 32 ms frame's peak decays by 10%,
 * so one loud sound cannot suppress a following quiet phrase for several seconds.
 * Normal-volume frames pass through unchanged; zero input remains zero.
 */
internal class SpeechDetectionLevel {
    private var peak = 0f

    fun process(frame: FloatArray): FloatArray {
        peak = max(frame.maxOfOrNull { abs(it) } ?: 0f, peak * .9f)
        val gain = (TARGET / peak.coerceAtLeast(TARGET / 16f)).coerceIn(1f, 16f)
        return if (gain == 1f) frame else FloatArray(frame.size) { frame[it] * gain }
    }

    fun reset() { peak = 0f }

    private companion object { const val TARGET = .2f }
}
