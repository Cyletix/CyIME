package com.kingzcheung.xime.speech.caption

import com.kingzcheung.xime.speech.LocalSpeechEngine
import com.kingzcheung.xime.speech.ResidentSession
import com.kingzcheung.xime.speech.SpeechBoundary
import com.kingzcheung.xime.speech.SpeechDetector
import com.kingzcheung.xime.speech.SpeechSegmenter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live-caption session: the same engine and segmenter as dictation, but every segment is its
 * own event with a start/end time, a second-pass final and, separately, a speaker label.
 * Unlike [com.kingzcheung.xime.speech.LocalSpeechSession] a late or failed refinement only
 * affects that segment; a caption session runs for hours and must never stop on one timeout.
 */
internal class CaptionSpeechSession(
    private val engine: LocalSpeechEngine,
    private val tracker: SpeakerTracker?,
    private val embed: EmbeddingSource?,
    private val listener: Listener,
    private val minimumPauseMs: Int = 400,
    private val refineTimeoutMs: Long = 8000,
) : ResidentSession {
    interface Listener {
        fun onSegment(id: Long, text: String, startMs: Long, endMs: Long, boundary: SpeechBoundary, confirmed: Boolean, event: String)
        fun onSpeaker(id: Long, speakerId: Int, confident: Boolean)
        fun onError(message: String)
    }

    private fun worker(name: String, capacity: Int) = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { task -> Thread(task, name).apply { isDaemon = true } })
    private val audio = worker("CyIME-Caption", 100)
    private val refinement = worker("CyIME-CaptionRefine", 4)
    private val speaker = worker("CyIME-Speaker", 4)
    private val lifecycle = Any()
    private val abandoned = AtomicBoolean(false)
    private var accepting = true

    // Region bookkeeping on the audio worker only.
    private var processedSamples = 0L
    private var activeId = 0L
    private var regionSamples = 0
    private var regionStartSample = 0L
    // Frames handed to onAudio before the next VAD call are the segmenter's pre-roll burst.
    private var detectorCalls = 0L
    private var burstCall = -1L
    private var burstSamples = 0
    private var lastFrameSpeech = false
    private var speechFrames = 0
    private var lastPreview = ""
    private val ended = HashSet<Long>()

    // Last 3 s of audio and per-frame VAD flags for the speaker windows (audio worker only).
    private val ring = FloatArray(48000)
    private val ringSpeech = BooleanArray(48000 / 512)
    private var ringFrames = 0L
    private var lastWindowEnd = 0L

    private val detector = engine.captionDetector()
    private val wrapped = object : SpeechDetector {
        override fun speech(frame: FloatArray): Boolean {
            detectorCalls++
            return detector.speech(frame).also { lastFrameSpeech = it; ringSpeech[(ringFrames++ % ringSpeech.size).toInt()] = it }
        }
        override fun reset() = detector.reset()
    }
    private val segmenter: SpeechSegmenter by lazy { SpeechSegmenter(wrapped,
        // Speakers change at pauses; a segment that runs through a pause mixes two voices into one print.
        maxSegmentMs = minOf(engine.maxSegmentMs, 5000),
        onAudio = { id, samples -> onAudio(id, samples) },
        onEnd = { id, samples, boundary -> endRegion(id, samples, boundary) },
        minimumPauseMs = maxOf(minimumPauseMs, if (engine.isStreaming) 0 else 300),
        // Through a room (TV, reverb) Silero alone hears speech across short turn-taking pauses
        // (device log: 8 s segments spanning two speakers); the adaptive energy floor splits them.
        useEnergyPause = true, resetDetectorOnPause = engine.resetDetectorOnPause) }

    private fun onAudio(id: Long, samples: FloatArray) {
        if (id != activeId) {
            activeId = id; regionSamples = 0; burstCall = detectorCalls; burstSamples = 0; lastPreview = ""
            speechFrames = if (lastFrameSpeech) 1 else 0
            lastFrameSpeech = false
        } else if (lastFrameSpeech) { speechFrames++; lastFrameSpeech = false }
        val inBurst = detectorCalls == burstCall
        if (inBurst) burstSamples += samples.size
        regionSamples += samples.size
        if (inBurst) regionStartSample = processedSamples - burstSamples
        provisionalSpeaker(id)
        if (engine.isStreaming) {
            val text = cleanCaptionText(engine.accept(samples))
            segmenter.charactersPerSecond = text.length / (regionSamples / 16000f).coerceAtLeast(.5f)
            if (text != lastPreview) {
                lastPreview = text
                listener.onSegment(id, text, regionStartSample / 16, processedSamples / 16, SpeechBoundary.PAUSE, false, "")
            }
        }
    }

    override fun acceptPcm(bytes: ByteArray) {
        require(bytes.size % 2 == 0) { "PCM 必须是 16 位单声道" }
        val samples = FloatArray(bytes.size / 2) { i ->
            (((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255)).toShort()).toFloat() / 32768f
        }
        synchronized(lifecycle) {
            if (!accepting || abandoned.get()) return
            try {
                audio.execute {
                    if (abandoned.get()) return@execute
                    try {
                        for (x in samples) { ring[(processedSamples % ring.size).toInt()] = x; processedSamples++ }
                        segmenter.accept(samples)
                        if (processedSamples - lastWindowEnd >= SpeakerTracker.HOP_MS * 16) speakerWindow()
                    } catch (e: Exception) { listener.onError("语音处理失败：${e.message}") }
                }
            } catch (_: RejectedExecutionException) {
                // Dropping a chunk loses 100 ms of audio; stopping the session would lose the evening.
                listener.onError("语音处理跟不上录音，丢弃了一段音频")
            }
        }
    }

    /** The last 2 s of audio, if mostly speech, goes to the speaker worker. */
    private fun speakerWindow(force: Boolean = false) {
        val tracker = tracker ?: return
        val embed = embed ?: return
        val length = (SpeakerTracker.WINDOW_MS * 16).toInt()
        if (processedSamples < length) return
        if (!force && processedSamples - lastWindowEnd < SpeakerTracker.HOP_MS * 16) return
        val frames = length / 512
        var voiced = 0
        for (k in 1..frames) if (ringSpeech[((ringFrames - k).mod(ringSpeech.size.toLong())).toInt()]) voiced++
        lastWindowEnd = processedSamples
        if (voiced < frames * 2 / 5) return
        val audio = FloatArray(length) { i -> ring[((processedSamples - length + i) % ring.size).toInt()] }
        val endMs = processedSamples / 16
        try {
            speaker.execute {
                if (abandoned.get()) return@execute
                val print = try { embed.embed(audio) } catch (e: Exception) { null } ?: return@execute
                publish(tracker.addWindow(endMs - SpeakerTracker.WINDOW_MS, endMs, print))
            }
        } catch (_: RejectedExecutionException) { /* a skipped window only delays labels */ }
    }

    private var provisionalFor = 0L

    /**
     * The sentence still being spoken goes on the track of the voice the windows hear right now
     * (marked not confident); the final label replaces it when the sentence ends. Without this,
     * a long monologue sat in a grey "正在说" row for seconds and looked like nobody's words.
     */
    private fun provisionalSpeaker(id: Long) {
        val tracker = tracker ?: return
        if (provisionalFor == id) return
        val recent = tracker.lastWindowEndMs
        if (recent < 0 || processedSamples / 16 - recent > 3000) return
        val who = tracker.lastWindowSpeaker
        if (who <= 0) return
        provisionalFor = id
        listener.onSpeaker(id, who, false)
    }

    /** The final text of a sentence, held until its speaker label is known so it can be split. */
    private class Final(val text: String, val tokens: Array<String>, val times: FloatArray, val startMs: Long, val endMs: Long,
        val boundary: SpeechBoundary, val event: String)

    private val join = Any()
    private val finals = HashMap<Long, Final>()
    private val firstLabels = HashMap<Long, SpeakerTracker.Label>()

    private fun publish(labels: List<SpeakerTracker.Label>) {
        for (label in labels) {
            if (!label.initial) { listener.onSpeaker(label.segmentId, label.speakerId, true); continue }
            val final = synchronized(join) {
                finals.remove(label.segmentId) ?: run { firstLabels[label.segmentId] = label; null }
            }
            if (final == null) listener.onSpeaker(label.segmentId, label.speakerId, true) else emitFinal(label.segmentId, final, label)
        }
    }

    /** Final text arrives (refinement or its fallback): emit now if the label is known, else hold it briefly. */
    private fun deliverFinal(id: Long, final: Final) {
        if (tracker == null) { listener.onSegment(id, final.text, final.startMs, final.endMs, final.boundary, true, final.event); return }
        val label = synchronized(join) { firstLabels.remove(id) ?: run { finals[id] = final; null } }
        if (label != null) { emitFinal(id, final, label); return }
        // The speaker worker has already queued this sentence's windows; behind them, release the text
        // unsplit if no label came (no speech window covered it).
        try {
            speaker.execute {
                val held = synchronized(join) { finals.remove(id) } ?: return@execute
                listener.onSegment(id, held.text, held.startMs, held.endMs, held.boundary, true, held.event)
            }
        } catch (_: RejectedExecutionException) {
            val held = synchronized(join) { finals.remove(id) } ?: return
            listener.onSegment(id, held.text, held.startMs, held.endMs, held.boundary, true, held.event)
        }
    }

    /**
     * Two people without a pause between them land in one recognition segment. When the speaker
     * windows inside it change voice, the text is cut at that time (SenseVoice token times, or in
     * proportion to time when there are none) and each piece goes to its own speaker.
     */
    private fun emitFinal(id: Long, final: Final, label: SpeakerTracker.Label) {
        val runs = label.runs
        val pieces = if (runs.size > 1) splitByTime(final, runs) else emptyList()
        if (pieces.size <= 1) {
            listener.onSegment(id, final.text, final.startMs, final.endMs, final.boundary, true, final.event)
            listener.onSpeaker(id, label.speakerId, true)
            return
        }
        pieces.forEachIndexed { k, (speakerId, from, to, text) ->
            val pieceId = if (k == 0) id else SPLIT_BASE + id * 16 + k
            listener.onSegment(pieceId, text, from, to, final.boundary, true, if (k == 0) final.event else "")
            listener.onSpeaker(pieceId, speakerId, true)
            tracker?.register(pieceId, from, to, speakerId)
        }
    }

    private data class Piece(val speakerId: Int, val fromMs: Long, val toMs: Long, val text: String)

    private fun splitByTime(final: Final, runs: List<Pair<Int, Long>>): List<Piece> {
        val bounds = runs.mapIndexed { i, (who, from) -> Triple(who, from, if (i + 1 < runs.size) runs[i + 1].second else final.endMs) }
        val texts = if (final.tokens.isNotEmpty() && final.tokens.size == final.times.size) {
            bounds.map { (_, from, to) ->
                val joined = buildString {
                    for (i in final.tokens.indices) {
                        val t = final.startMs + (final.times[i] * 1000).toLong()
                        if (t >= from && (t < to || to == final.endMs)) append(final.tokens[i].replace('▁', ' '))
                    }
                }
                cleanCaptionText(joined)
            }
        } else {
            // No token times: cut the characters in proportion to each voice's share of the time.
            val total = (final.endMs - final.startMs).coerceAtLeast(1)
            var cursor = 0
            bounds.mapIndexed { i, (_, from, to) ->
                val end = if (i == bounds.lastIndex) final.text.length
                    else (cursor + final.text.length * (to - from) / total).toInt().coerceIn(cursor, final.text.length)
                final.text.substring(cursor, end).trim().also { cursor = end }
            }
        }
        // Never invent or drop words: if the pieces lost text (rare tokenizer mismatch), keep it whole.
        if (texts.joinToString("").filter { it.isLetterOrDigit() }.length < final.text.filter { it.isLetterOrDigit() }.length * 0.8) return emptyList()
        return bounds.zip(texts).filter { it.second.isNotBlank() }.map { (b, text) -> Piece(b.first, b.second, b.third, text) }
    }

    private fun endRegion(id: Long, samples: FloatArray, boundary: SpeechBoundary) {
        ended.add(id)
        val startMs = regionStartSample / 16
        val endMs = processedSamples / 16
        val preview = if (engine.isStreaming) cleanCaptionText(engine.finishStream()) else ""
        val needsRefine = engine.hasRefinement || !engine.isStreaming
        // The preview stays live (not closed) until the final text and its speaker are both known.
        listener.onSegment(id, preview, startMs, endMs, boundary, false, "")
        if (!needsRefine) deliverFinal(id, Final(preview, emptyArray(), FloatArray(0), startMs, endMs, boundary, ""))
        if (needsRefine) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(refineTimeoutMs)
            try {
                refinement.execute {
                    if (abandoned.get()) return@execute
                    try {
                        check(System.nanoTime() < deadline) { "离线识别积压" }
                        val rich = engine.refineRich(samples)
                        val refined = cleanCaptionText(rich.text, rich.language)
                        val event = captionEvent(rich.event)
                        val text = chooseFinal(preview, refined)
                        // Token times only describe SenseVoice's own text.
                        val timed = text == refined
                        deliverFinal(id, Final(text, if (timed) rich.tokens else emptyArray(), if (timed) rich.timestamps else FloatArray(0),
                            startMs, endMs, boundary, event))
                    } catch (e: Exception) {
                        // Keep the preview as the final text for this segment only.
                        deliverFinal(id, Final(preview, emptyArray(), FloatArray(0), startMs, endMs, boundary, "timeout"))
                    }
                }
            } catch (_: RejectedExecutionException) {
                deliverFinal(id, Final(preview, emptyArray(), FloatArray(0), startMs, endMs, boundary, "timeout"))
            }
        }
        if (tracker == null) { listener.onSpeaker(id, CaptionSpeakers.SINGLE, true); return }
        // Cover the sentence's tail, then label it from the windows it spans.
        speakerWindow(force = true)
        try {
            speaker.execute { if (!abandoned.get()) publish(tracker.addSegment(id, startMs, endMs)) }
        } catch (_: RejectedExecutionException) { }
    }

    override fun finish(): String {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(refineTimeoutMs + 2000)
        synchronized(lifecycle) {
            if (accepting) {
                accepting = false
                val queued = audio.queue.offer(Runnable {
                    if (!abandoned.get()) try { segmenter.finishInput() }
                    catch (e: Exception) { listener.onError("语音收尾失败：${e.message}") }
                }, 100, TimeUnit.MILLISECONDS)
                if (!queued) audio.queue.clear()
                audio.prestartCoreThread()
                audio.shutdown()
            }
        }
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(0)
        audio.awaitTermination(remaining(), TimeUnit.NANOSECONDS)
        refinement.shutdown()
        refinement.awaitTermination(remaining(), TimeUnit.NANOSECONDS)
        tracker?.let { t -> try { speaker.execute { publish(t.settle()) } } catch (_: RejectedExecutionException) { } }
        speaker.shutdown()
        speaker.awaitTermination(remaining(), TimeUnit.NANOSECONDS)
        abandoned.set(true)
        return ""
    }

    override fun cancel() {
        abandoned.set(true)
        synchronized(lifecycle) { accepting = false; audio.queue.clear(); audio.shutdown() }
        refinement.queue.clear(); refinement.shutdown()
        speaker.queue.clear(); speaker.shutdown()
    }

    override fun awaitIdle() {
        while (!audio.awaitTermination(1, TimeUnit.SECONDS)) { }
        while (!refinement.awaitTermination(1, TimeUnit.SECONDS)) { }
        while (!speaker.awaitTermination(1, TimeUnit.SECONDS)) { }
        engine.reset()
    }

    override val isIdle: Boolean get() = audio.isTerminated && refinement.isTerminated && speaker.isTerminated
}

internal object CaptionSpeakers { const val SINGLE = 0 }

/** Ids of sentence pieces split off at a change of voice; far above anything the segmenter numbers. */
internal const val SPLIT_BASE = 1L shl 40

/**
 * Two-pass choice per sentence. SenseVoice normally wins (CER 7.6 % vs 15.1 % on FLEURS), but when
 * it returns far fewer characters than the streaming pass heard, it dropped speech: keep the preview.
 */
internal fun chooseFinal(preview: String, refined: String): String {
    // Captions are Chinese only: a second pass in another script (kana, Hangul…) never replaces the Mandarin preview.
    if (com.kingzcheung.xime.speech.hasForeignScript(refined)) return preview
    fun count(text: String) = text.count { it.isLetterOrDigit() }
    val heard = count(preview)
    return if (heard >= 4 && count(refined) * 2 < heard) preview else refined
}
