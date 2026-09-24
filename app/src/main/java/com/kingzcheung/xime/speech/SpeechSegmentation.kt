package com.kingzcheung.xime.speech

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Adapted from ASRInput EnergyPauseGate (MIT, Cyletix); estimates pauses, never gates PCM. */
internal class SpeechEnergyGate {
    private val recent = ArrayDeque<Float>()
    private var floor = .0001f
    private var contrast = 0f
    private var frames = 0
    private var unvoiced = 0
    fun quiet(rms: Float, vadSpeech: Boolean, speaking: Boolean): Boolean {
        recent.addLast(rms)
        if (recent.size > 125) recent.removeFirst()
        frames++
        unvoiced = if (!vadSpeech && !speaking) unvoiced + 1 else 0
        if (recent.size >= 16 && frames % 8 == 0) {
            val sorted = recent.sorted()
            val low = sorted[((sorted.size - 1) * .15).toInt()]
            val high = sorted[((sorted.size - 1) * .85).toInt()]
            val target = if (high > max(low * 2.5f, .0004f))
                min(high * .24f, low + (high - low) * .15f) else 0f
            contrast = contrast * .65f + target * .35f
            if (unvoiced >= 16) {
                val tail = recent.takeLast(16).sorted()
                if (tail[13] <= max(.0002f, tail[2] * 1.5f)) floor = floor * .75f + tail[8] * .25f
            }
        }
        return rms < max(floor * 1.6f, contrast).coerceIn(.0001f, .5f)
    }
}

internal interface SpeechDetector {
    fun speech(frame: FloatArray): Boolean
    fun reset()
}

internal enum class SpeechBoundary { PAUSE, LIMIT, STOP }

/** 32 ms acoustic frames, 1024 ms pre-roll, adaptive pauses. Every sample is decoded at most once. */
internal class SpeechSegmenter(
    private val detector: SpeechDetector,
    private val maxSegmentMs: Int,
    private val onAudio: (Long, FloatArray) -> Unit,
    private val onEnd: (Long, FloatArray, SpeechBoundary) -> Unit,
    private val minimumPauseMs: Int = 0,
) {
    private val energy = SpeechEnergyGate()
    private val preRoll = ArrayDeque<FloatArray>()
    private val audio = ArrayList<FloatArray>()
    private val pending = FloatArray(512)
    private var pendingSize = 0
    private var id = 0L
    private var samples = 0
    private var quietFrames = 0
    private var speaking = false
    private var continuation = false
    var charactersPerSecond = 0f

    fun accept(input: FloatArray) {
        var offset = 0
        while (offset < input.size) {
            val n = min(512 - pendingSize, input.size - offset)
            input.copyInto(pending, pendingSize, offset, offset + n)
            offset += n; pendingSize += n
            if (pendingSize == 512) { frame(pending.copyOf()); pendingSize = 0 }
        }
    }

    private fun frame(pcm: FloatArray) {
        // Only VAD needs padding. Do not invent PCM at stop or feed padding into the next segment.
        val active = detector.speech(if (pcm.size == 512) pcm else pcm.copyOf(512))
        val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size).toFloat()
        val energyQuiet = energy.quiet(rms, active, speaking)
        val quiet = !active || energyQuiet
        if (!speaking) {
            preRoll.addLast(pcm)
            while (preRoll.size > 32) preRoll.removeFirst()
            if ((!active || quiet) && !continuation) return
            speaking = true; continuation = false; id++; quietFrames = 0; charactersPerSecond = 0f
            for (part in preRoll) { audio.add(part); samples += part.size; onAudio(id, part) }
            preRoll.clear()
        } else {
            audio.add(pcm); samples += pcm.size; onAudio(id, pcm)
            quietFrames = if (quiet) quietFrames + 1 else 0
        }
        val pauseMs = max(minimumPauseMs, when {
            samples < 24000 || charactersPerSecond in .01f..3.5f -> 600
            charactersPerSecond >= 6.5f -> 256
            else -> 384
        })
        when {
            quietFrames * 32 >= pauseMs -> finish(SpeechBoundary.PAUSE)
            // Prefer a low-energy break near the cap before forcing an acoustic cut.
            samples >= maxSegmentMs * 16 && quietFrames >= 8 -> finish(SpeechBoundary.PAUSE)
            samples >= (maxSegmentMs + 2000) * 16 -> finish(SpeechBoundary.LIMIT)
        }
    }

    fun finishInput() {
        if (pendingSize > 0) { frame(pending.copyOf(pendingSize)); pendingSize = 0 }
        if (speaking) finish(SpeechBoundary.STOP)
        preRoll.clear()
        detector.reset()
    }

    private fun finish(reason: SpeechBoundary) {
        val all = FloatArray(samples)
        var offset = 0
        audio.forEach { it.copyInto(all, offset); offset += it.size }
        onEnd(id, all, reason)
        audio.clear(); preRoll.clear(); samples = 0; quietFrames = 0; speaking = false
        // At a hard limit VAD can still be speaking. Keep the next frame, without pre-roll overlap.
        continuation = reason == SpeechBoundary.LIMIT
    }
}

private val richTags = Regex("<\\|[^|]*\\|>")
private val speechPunctuation = Regex("[\\p{P}]")
private val speechWhitespace = Regex("[\\p{Z}\\s]+")

/** Model-added sentence punctuation becomes spacing; numeric notation keeps its meaning.
 * Spoken punctuation names stay as words until the final UI boundary, so repeated local
 * cleanup (SenseVoice -> transcript -> UI) cannot erase an explicitly dictated symbol.
 */
internal fun cleanSpeechText(text: String): String {
    val plain = richTags.replace(text, "")
    fun neighbor(from: Int, step: Int): Char? {
        var index = from
        while (index in plain.indices) {
            if (!plain[index].isWhitespace()) return plain[index]
            index += step
        }
        return null
    }
    fun startsNumber(from: Int): Boolean {
        var index = from
        while (index in plain.indices && plain[index].isWhitespace()) index++
        if (plain.getOrNull(index) in listOf('+', '-', '−', '－')) index++
        while (index in plain.indices && plain[index].isWhitespace()) index++
        val first = plain.getOrNull(index)
        return first?.isDigit() == true || first == '(' || first == '（' ||
            (first == '.' && plain.getOrNull(index + 1)?.isDigit() == true)
    }
    return speechWhitespace.replace(buildString {
        plain.forEachIndexed { i, c ->
            if (!speechPunctuation.matches(c.toString())) {
                append(c)
                return@forEachIndexed
            }
            val before = plain.getOrNull(i - 1)
            val after = plain.getOrNull(i + 1)
            val left = neighbor(i - 1, -1)
            val right = neighbor(i + 1, 1)
            val numeric = when (c) {
                '.', '．' -> after?.isDigit() == true && (before?.isDigit() == true ||
                    before == null || before.isWhitespace() || before in "+-−×÷/*=(（")
                ',' -> before?.isDigit() == true && after?.isDigit() == true
                '-', '－' -> startsNumber(i + 1)
                '/', '／', '*', '＊' -> (left?.isDigit() == true || left == ')' || left == '）') &&
                    startsNumber(i + 1)
                ':', '：' -> left?.isDigit() == true && right?.isDigit() == true
                '%', '％' -> left?.isDigit() == true
                '(', '（' -> startsNumber(i + 1)
                ')', '）' -> left?.isDigit() == true || left == '%' || left == '％' || left == ')' || left == '）'
                else -> false
            }
            append(if (numeric) c else ' ')
        }
    }, " ").trim()
}

// Remove SenseVoice's CJK token-spacing before punctuation normalization so acoustic
// and punctuation boundaries survive. Latin word spacing is never removed.
internal fun cleanSenseVoiceText(text: String): String {
    val plain = richTags.replace(text, "")
    fun cjk(c: Char) = c in '\u3040'..'\u30ff' || c in '\u3400'..'\u9fff'
    val joined = buildString {
        var i = 0
        while (i < plain.length) {
            val c = plain[i]
            if (!c.isWhitespace()) { append(c); i++; continue }
            var next = i + 1
            while (next < plain.length && plain[next].isWhitespace()) next++
            val left = lastOrNull()
            val right = plain.getOrNull(next)
            val tokenSpace = left != null && right != null &&
                (cjk(left) && (cjk(right) || right.isDigit()) || left.isDigit() && cjk(right))
            if (!tokenSpace) append(' ')
            i = next
        }
    }
    return cleanSpeechText(joined)
}

/** Identified regions allow late second-pass results to replace exactly their preview. */
internal class SpeechTranscript {
    private data class Region(var text: String = "", var boundary: SpeechBoundary = SpeechBoundary.STOP)
    private val regions = sortedMapOf<Long, Region>()
    fun update(id: Long, text: String, boundary: SpeechBoundary? = null): String {
        val region = regions.getOrPut(id) { Region() }
        region.text = cleanSpeechText(text)
        if (boundary != null) region.boundary = boundary
        return text()
    }
    fun text(): String = buildString {
        var previous = SpeechBoundary.STOP
        for (region in regions.values) {
            if (region.text.isNotEmpty()) {
                if (isNotEmpty() && (previous != SpeechBoundary.LIMIT ||
                    last().isLetterOrDigit() && last().code < 128 && region.text.first().code < 128)) append(' ')
                append(region.text)
            }
            previous = region.boundary
        }
    }
}
