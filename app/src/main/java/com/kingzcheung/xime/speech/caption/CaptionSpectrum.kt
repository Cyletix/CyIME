package com.kingzcheung.xime.speech.caption

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Loudness of the voice range in a few log-spaced bands (0..1), for the sound ribbon on the
 * caption page. Same idea as Cylenx's menu spectrum: 8 bands, dB mapped onto a fixed window so
 * a quiet room lies flat and speech lifts the ribbon.
 */
internal class CaptionSpectrum {
    private val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
    private val windowSum = window.sum()
    private val re = FloatArray(SIZE)
    private val im = FloatArray(SIZE)
    private val edges = IntArray(BANDS + 1) { i ->
        val hz = exp(ln(MIN_HZ) + (ln(MAX_HZ) - ln(MIN_HZ)) * i / BANDS)
        (hz * SIZE / CaptionRecorder.SAMPLE_RATE).toInt().coerceIn(1, SIZE / 2)
    }

    /** Bands of [SIZE] PCM16 samples starting at sample [from] of [pcm]. */
    fun bands(pcm: ByteArray, from: Int): FloatArray {
        for (i in 0 until SIZE) {
            val at = (from + i) * 2
            val sample = if (at + 1 < pcm.size) ((pcm[at + 1].toInt() shl 8) or (pcm[at].toInt() and 255)).toShort() / 32768f else 0f
            re[i] = sample * window[i]; im[i] = 0f
        }
        fft()
        return FloatArray(BANDS) { b ->
            var power = 0f
            val lo = edges[b]; val hi = maxOf(edges[b + 1], lo + 1)
            for (k in lo until hi) power += re[k] * re[k] + im[k] * im[k]
            val magnitude = sqrt(power / (hi - lo)) * 2f / windowSum
            val db = 20f * log10(magnitude.coerceAtLeast(1e-9f))
            ((db - FLOOR_DB) / RANGE_DB).coerceIn(0f, 1f)
        }
    }

    private fun fft() {
        var j = 0
        for (i in 1 until SIZE) {
            var bit = SIZE shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= SIZE) {
            val angle = -2 * PI / len
            for (start in 0 until SIZE step len) for (k in 0 until len / 2) {
                val wr = cos(angle * k).toFloat(); val wi = sin(angle * k).toFloat()
                val a = start + k; val b = a + len / 2
                val xr = re[b] * wr - im[b] * wi; val xi = re[b] * wi + im[b] * wr
                re[b] = re[a] - xr; im[b] = im[a] - xi
                re[a] += xr; im[a] += xi
            }
            len = len shl 1
        }
    }

    companion object {
        const val SIZE = 512 // 32 ms
        const val BANDS = 8
        private const val MIN_HZ = 90.0
        private const val MAX_HZ = 7000.0
        // Room noise on VOICE_RECOGNITION sits around -75..-65 dBFS per band; close speech near -30.
        private const val FLOOR_DB = -66f
        private const val RANGE_DB = 36f
    }
}

/**
 * Takes the room out of [CaptionSpectrum]'s bands, so the ribbon lies flat in silence on any
 * microphone. The fixed dB window was tuned on the tablet, whose quiet room sits at the bottom of it;
 * a phone with a hotter microphone (SM-S9180, 2026-10-30) put its room hum well inside the window,
 * and the ribbon stood as a big wave that never moved. Each band keeps a floor that drops quickly to
 * quieter frames and creeps up slowly, so pauses between words reset it while speech stays above it.
 * Bands come out as their height above that floor; a whole frame whose average barely clears its
 * own floor counts as silence (single bands of hum flicker by several dB, the average does not).
 */
internal class RoomFloor(private val bands: Int = CaptionSpectrum.BANDS) {
    private val floor = FloatArray(bands)
    private var meanFloor = 0f
    private var started = false

    fun above(levels: FloatArray): FloatArray {
        val mean = levels.average().toFloat()
        if (!started) {
            levels.copyInto(floor); meanFloor = mean; started = true
        }
        for (b in 0 until bands) floor[b] = follow(floor[b], levels[b])
        meanFloor = follow(meanFloor, mean)
        val open = ((mean - meanFloor - GATE_START) / (GATE_FULL - GATE_START)).coerceIn(0f, 1f)
        return FloatArray(bands) { b -> (levels[b] - floor[b]).coerceAtLeast(0f) * open }
    }

    private fun follow(floor: Float, level: Float) = floor + (level - floor) * if (level < floor) FALL else RISE

    companion object {
        // Per ~33 ms frame: down in about a tenth of a second, up over several seconds.
        private const val FALL = .25f
        private const val RISE = .008f
        // Average above its floor (window units, 36 dB each): below 2.9 dB is the room, 5.8 dB counts fully.
        private const val GATE_START = .08f
        private const val GATE_FULL = .16f
    }
}
