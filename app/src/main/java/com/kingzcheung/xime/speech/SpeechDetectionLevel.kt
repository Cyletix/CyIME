package com.kingzcheung.xime.speech

import kotlin.math.abs
import kotlin.math.max

/** Bounded gain for VAD only. The recognizer always receives the original PCM.
 * The 0.05 peak target and 16x cap recover attenuated speech without normalizing
 * arbitrary near-zero noise to full scale. A 32 ms frame's peak decays by 10%,
 * so one loud sound cannot suppress a following quiet phrase for several seconds.
 * Normal-volume frames pass through unchanged; zero input remains zero.
 */
internal class SpeechDetectionLevel {
    private var peak = 0f

    fun process(frame: FloatArray): FloatArray {
        peak = max(frame.maxOfOrNull { abs(it) } ?: 0f, peak * .9f)
        val gain = (.05f / peak.coerceAtLeast(.05f / 16f)).coerceIn(1f, 16f)
        return if (gain == 1f) frame else FloatArray(frame.size) { frame[it] * gain }
    }

    fun reset() { peak = 0f }
}
