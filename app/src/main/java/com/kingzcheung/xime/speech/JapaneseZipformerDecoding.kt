package com.kingzcheung.xime.speech

/** Same policy as ASRInput/run_zipformer_adaptive.py; no reference text or word rewriting. */
internal object JapaneseZipformerDecoding {
    const val paddingSamples = 14400
    const val decodeTimeoutMs = 15000L
    // Session drain allows another 2 s; capture/UI watchdogs must outlive that drain.
    const val backendTimeoutMs = decodeTimeoutMs + 3000
    data class Output(val text: String, val tokens: List<String>, val timestamps: List<Float>)

    fun decode(samples: FloatArray, recognize: (FloatArray) -> Output,
               speech: (FloatArray) -> List<Pair<Double, Double>>, depth: Int = 0): String {
        val padded = FloatArray(samples.size + paddingSamples * 2)
        samples.copyInto(padded, paddingSamples)
        val result = recognize(padded)
        val spans = speech(samples)
        if (depth < 3 && samples.size >= 64000 && suspectGap(result.timestamps, samples.size / 16000.0, spans)) {
            val cut = quietCut(samples)
            return listOf(decode(samples.copyOfRange(0, cut), recognize, speech, depth + 1),
                decode(samples.copyOfRange(cut, samples.size), recognize, speech, depth + 1))
                .filter { it.isNotBlank() }.joinToString(" ")
        }
        return spaced(result)
    }

    fun suspectGap(stamps: List<Float>, duration: Double, spans: List<Pair<Double, Double>>): Boolean {
        val times = (listOf(0.0, duration) + stamps.map { (it.toDouble() - .9).coerceIn(0.0, duration) }).distinct().sorted()
        return times.zipWithNext().any { (left, right) ->
            val voiced = spans.sumOf { (a, b) -> (minOf(right, b) - maxOf(left, a)).coerceAtLeast(0.0) }
            right - left >= 2.8 && voiced >= 1.3 && voiced / (right - left) >= .5
        }
    }

    fun quietCut(samples: FloatArray): Int {
        val power = DoubleArray((samples.size + 319) / 320) { frame ->
            val end = minOf(samples.size, (frame + 1) * 320)
            (frame * 320 until end).sumOf { samples[it].toDouble() * samples[it] } / (end - frame * 320)
        }
        // numpy.convolve(power, ones(8)/8, mode="same"): even kernel is left-biased.
        val smoothed = DoubleArray(power.size) { i -> (i - 4..i + 3).sumOf { power.getOrElse(it) { 0.0 } } / 8 }
        val lo = maxOf(1, (samples.size * .35).toInt() / 320)
        val hi = minOf(power.size - 1, (samples.size * .65).toInt() / 320)
        return (lo..hi).minBy { smoothed[it] } * 320
    }

    /** Conservative timestamp spacing; uncertain token alignment leaves the text untouched. */
    fun spaced(result: Output): String {
        val tokens = result.tokens.map { it.replace('▁', ' ') }
        if (tokens.joinToString("").trim() != result.text.trim() || tokens.size != result.timestamps.size) return result.text
        return buildString {
            tokens.forEachIndexed { i, token ->
                val first = token.trimStart().firstOrNull()
                val startsPhrase = first != null && (first in '\u3400'..'\u9fff' ||
                    first in '\u30a1'..'\u30fa' && first !in "ァィゥェォッャュョヮ")
                // The first token can have timestamp 0 even when the word starts much later.
                if (i > 1 && result.timestamps[i] - result.timestamps[i - 1] >= .65f && startsPhrase) append(' ')
                append(token)
            }
        }.trim()
    }
}
