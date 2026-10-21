package com.kingzcheung.xime.speech

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface LocalSpeechEngine : AutoCloseable {
    val hasRefinement: Boolean
    val isStreaming: Boolean
    val maxSegmentMs: Int get() = if (hasRefinement || !isStreaming) 6000 else 12000
    // Streaming text is already visible, so a longer pause only delays the segment cut, not
    // the preview. 2026-10-13 FLEURS replay: Paraformer 18.0%→15.1% CER, with SenseVoice 8.8%→7.6%.
    val minimumPauseMs: Int get() = if (isStreaming) 1000 else 600
    val useEnergyPause: Boolean get() = true
    val resetDetectorOnPause: Boolean get() = false
    val decodeTimeoutMs: Long get() = 5000
    val hasRollingPreview: Boolean get() = false
    fun preview(samples: FloatArray): String = error("This recognizer has no rolling preview")
    /** Captions may re-read the sentence so far for a live line when there is no streaming model. */
    val hasCaptionPreview: Boolean get() = false
    fun detector(): SpeechDetector
    /** Captions: the VAD-only quiet-speech gain on every engine (the keyboard applies it to Paraformer). */
    fun captionDetector(): SpeechDetector = detector()
    fun accept(samples: FloatArray): String
    fun finishStream(): String
    fun refine(samples: FloatArray): String
    /** Captions keep punctuation and the event label; dictation keeps using [refine]. */
    fun refineRich(samples: FloatArray): RichTranscript = RichTranscript(refine(samples))
    /** Decoding is pinned to Chinese: a second pass that still comes back as kana is rejected. */
    val lockedChinese: Boolean get() = false
    fun reset()
    /**
     * 语音内存阶梯第一级：只释放第二遍识别器、保留流式模型。下次停顿定稿时再加载。
     * 只能在没有会话使用引擎时调用；返回是否确实释放了。
     */
    fun releaseRefinement(): Boolean = false
}

/** Text in a script Chinese speech never produces: kana, Hangul, Thai, Lao, Myanmar, Khmer, Indic, Arabic, Cyrillic, Hebrew. */
private val FOREIGN = Regex("[\u0400-\u04FF\u0590-\u06FF\u0900-\u0D7F\u0E00-\u0EFF\u1000-\u109F\u1780-\u17FF\u3040-\u30FF\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]")
internal fun hasForeignScript(text: String): Boolean = FOREIGN.containsMatchIn(text)

/**
 * Hard guard for Chinese speech: SenseVoice (and any later correction model) can still answer in
 * another script on short, phone-quality Mandarin. Such a result is dropped and the streaming text
 * (or nothing) stays.
 */
internal fun keepChinese(preview: String, refined: String, locked: Boolean): String =
    if (locked && hasForeignScript(refined)) preview else refined

/** Second-pass result before text cleanup: [event] is the model's <|…|> label or empty. */
internal class RichTranscript(val text: String, val language: String = "", val event: String = "",
    /** Token pieces and their start times in seconds from the start of the refined audio (may be empty). */
    val tokens: Array<String> = emptyArray(), val timestamps: FloatArray = FloatArray(0))

/** Bounded queues: capture never waits for decode; refinements cannot overwrite another region/session. */
internal class LocalSpeechSession(
    private val engine: LocalSpeechEngine,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onRegion: (Long, Int, SpeechBoundary) -> Unit = { _, _, _ -> },
) : ResidentSession {
    private fun worker(name: String, capacity: Int) = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { task -> Thread(task, name).apply { isDaemon = true } })
    private val audio = worker("CyIME-Speech", 100) // 10 s of 100 ms PCM, bounded on low-end CPUs
    private val refinement = worker("CyIME-Refinement", 2)
    private val lifecycle = Any()
    private val outputLock = Any()
    private val transcript = SpeechTranscript()
    private val pending = mutableSetOf<Long>()
    private val unconfirmed = mutableSetOf<Long>()
    private val ended = mutableMapOf<Long, SpeechBoundary>()
    private val abandoned = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val resetLock = Any()
    private var resetComplete = false
    private var accepting = true
    private var previous = ""
    private var segmentSamples = 0
    private var activeId = 0L
    private val previewAudio = ArrayList<FloatArray>()
    private val previewBusy = AtomicBoolean(false)
    private val segmenter: SpeechSegmenter by lazy { SpeechSegmenter(engine.detector(),
        maxSegmentMs = engine.maxSegmentMs,
        onAudio = { id, samples ->
            if (id != activeId) { activeId = id; segmentSamples = 0; previewAudio.clear() }
            segmentSamples += samples.size
            if (engine.isStreaming) {
                val text = engine.accept(samples)
                segmenter.charactersPerSecond = cleanSpeechText(text).length / (segmentSamples / 16000f).coerceAtLeast(.5f)
                publish(id, text)
            } else if (engine.hasRollingPreview) {
                previewAudio.add(samples)
            }
        },
        onEnd = { id, samples, boundary -> endRegion(id, samples, boundary) },
        minimumPauseMs = engine.minimumPauseMs, useEnergyPause = engine.useEnergyPause,
        resetDetectorOnPause = engine.resetDetectorOnPause) }

    /** Called after a capture chunk, including all VAD pre-roll; no timer or minimum audio window. */
    private fun previewLatestAudio() {
        if (!engine.hasRollingPreview || previewAudio.isEmpty() || !previewBusy.compareAndSet(false, true)) return
        val snapshot = FloatArray(segmentSamples)
        var offset = 0
        previewAudio.forEach { it.copyInto(snapshot, offset); offset += it.size }
        schedulePreview(activeId, snapshot)
    }

    /** At most one speculative decode; shares the final worker so native inference never races. */
    private fun schedulePreview(id: Long, samples: FloatArray) {
        try {
            refinement.execute {
                try {
                    if (abandoned.get() || failed.get() || synchronized(outputLock) { id in ended }) return@execute
                    val text = engine.preview(samples)
                    synchronized(outputLock) { if (id !in ended) publish(id, text) }
                } catch (e: Exception) {
                    fail("语音预览失败：${e.message}")
                } finally { previewBusy.set(false) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Finals take priority; never accumulate a stale preview or lose captured audio.
            previewBusy.set(false)
        }
    }

    override fun acceptPcm(bytes: ByteArray) {
        require(bytes.size % 2 == 0) { "PCM 必须是 16 位单声道" }
        val samples = FloatArray(bytes.size / 2) { i ->
            (((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255)).toShort()).toFloat() / 32768f
        }
        synchronized(lifecycle) {
            if (!accepting || abandoned.get() || failed.get()) return
            try {
                audio.execute {
                    if (!abandoned.get() && !failed.get()) try {
                        segmenter.accept(samples)
                        previewLatestAudio()
                    }
                    catch (e: Exception) { fail("语音处理失败：${e.message}") }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                fail("语音处理跟不上录音，已停止接收；请改用单模型后重试")
            }
        }
    }

    private fun endRegion(id: Long, samples: FloatArray, boundary: SpeechBoundary) {
        synchronized(outputLock) { ended[id] = boundary }
        onRegion(id, samples.size, boundary)
        previewAudio.clear()
        val preview = if (engine.isStreaming) engine.finishStream() else ""
        if (engine.isStreaming) publish(id, preview, boundary, confirmed = !engine.hasRefinement)
        if (engine.hasRefinement || !engine.isStreaming) {
            synchronized(outputLock) { pending.add(id) }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(engine.decodeTimeoutMs)
            try {
                refinement.execute {
                    if (abandoned.get()) return@execute
                    try {
                        check(System.nanoTime() < deadline) { "离线识别积压" }
                        val text = keepChinese(preview, engine.refine(samples), engine.lockedChinese)
                        check(System.nanoTime() < deadline) { "离线识别超时" }
                        synchronized(outputLock) { pending.remove(id); publish(id, text, boundary, confirmed = true) }
                    } catch (e: Exception) {
                        synchronized(outputLock) { pending.remove(id); publish(id, "", boundary) }
                        fail(if (engine.hasRefinement) "多语定稿失败：${e.message}；未将中英预览作为最终文本" else "离线识别失败：${e.message}")
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                synchronized(outputLock) { pending.remove(id); publish(id, "", boundary) }
                fail(if (engine.hasRefinement) "第二遍识别队列已满，请关闭 SenseVoice 二次校正后重试" else "离线识别跟不上录音，请缩短录音后重试")
            }
        }
    }

    private fun publish(id: Long, text: String, boundary: SpeechBoundary? = null, confirmed: Boolean = false) = synchronized(outputLock) {
        if (abandoned.get() || failed.get()) return@synchronized
        if (confirmed) unconfirmed.remove(id) else unconfirmed.add(id)
        val result = transcript.update(id, text, boundary)
        if (result != previous) { previous = result; onText(result) }
    }
    private fun fail(message: String) {
        if (failed.compareAndSet(false, true) && !abandoned.get()) synchronized(outputLock) {
            unconfirmed.forEach { transcript.update(it, "", ended[it]) }
            unconfirmed.clear()
            val safe = transcript.text()
            if (safe != previous) { previous = safe; onText(safe) }
            onError(message)
        }
    }

    override fun finish(): String = finish(engine.decodeTimeoutMs + 2000)

    /** Drains actual audio, then waits only for outstanding work; no artificial stop delay. */
    fun finish(timeoutMs: Long): String {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(lifecycle) {
            if (accepting) {
                accepting = false
                // Allow a bounded wait for the stop marker; report overload instead of dropping it silently.
                val queued = audio.queue.offer(Runnable {
                    if (!abandoned.get()) try { segmenter.finishInput() }
                    catch (e: Exception) { fail("语音收尾失败：${e.message}") }
                }, 100, TimeUnit.MILLISECONDS)
                if (!queued) { fail("语音收尾队列已满，未完成的预览不会作为最终结果"); audio.queue.clear() }
                audio.prestartCoreThread()
                audio.shutdown()
            }
        }
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(0)
        val drained = audio.awaitTermination(remaining(), TimeUnit.NANOSECONDS)
        refinement.shutdown()
        val refined = refinement.awaitTermination(remaining(), TimeUnit.NANOSECONDS)
        synchronized(outputLock) {
            if (!drained || !refined) {
                pending.forEach { transcript.update(it, "", ended[it]) }
                pending.clear()
                fail("语音收尾超时，未完成的预览不会作为最终结果")
            }
            abandoned.set(true)
            return transcript.text()
        }
    }

    override fun cancel() {
        abandoned.set(true)
        synchronized(lifecycle) { accepting = false; audio.queue.clear(); audio.shutdown() }
        refinement.queue.clear(); refinement.shutdown()
    }
    override fun awaitIdle() {
        while (!audio.awaitTermination(1, TimeUnit.SECONDS)) { }
        while (!refinement.awaitTermination(1, TimeUnit.SECONDS)) { }
        // More than one retirement/start waiter may observe termination. A later waiter
        // must neither reset the next session nor leave before the first reset completes.
        synchronized(resetLock) {
            if (!resetComplete) {
                engine.reset()
                resetComplete = true
            }
        }
    }
    override val isIdle: Boolean get() = audio.isTerminated && refinement.isTerminated
}
