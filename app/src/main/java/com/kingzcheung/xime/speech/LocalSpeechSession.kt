package com.kingzcheung.xime.speech

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface LocalSpeechEngine : AutoCloseable {
    val hasRefinement: Boolean
    val isStreaming: Boolean
    fun detector(): SpeechDetector
    fun accept(samples: FloatArray): String
    fun finishStream(): String
    fun refine(samples: FloatArray): String
    fun reset()
}

/** Bounded queues: capture never waits for decode; refinements cannot overwrite another region/session. */
internal class LocalSpeechSession(
    private val engine: LocalSpeechEngine,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onRegion: (Long, Int, SpeechBoundary) -> Unit = { _, _, _ -> },
) {
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
    private var accepting = true
    private var previous = ""
    private var segmentSamples = 0
    private var activeId = 0L
    private val segmenter: SpeechSegmenter by lazy { SpeechSegmenter(engine.detector(),
        maxSegmentMs = if (engine.hasRefinement || !engine.isStreaming) 6000 else 12000,
        onAudio = { id, samples ->
            if (id != activeId) { activeId = id; segmentSamples = 0 }
            segmentSamples += samples.size
            if (engine.isStreaming) {
                val text = engine.accept(samples)
                segmenter.charactersPerSecond = cleanSpeechText(text).length / (segmentSamples / 16000f).coerceAtLeast(.5f)
                publish(id, text)
            }
        },
        onEnd = { id, samples, boundary -> endRegion(id, samples, boundary) },
        minimumPauseMs = if (engine.hasRefinement || !engine.isStreaming) 600 else 0) }

    fun acceptPcm(bytes: ByteArray) {
        require(bytes.size % 2 == 0) { "PCM 必须是 16 位单声道" }
        val samples = FloatArray(bytes.size / 2) { i ->
            (((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255)).toShort()).toFloat() / 32768f
        }
        synchronized(lifecycle) {
            if (!accepting || abandoned.get() || failed.get()) return
            try {
                audio.execute {
                    if (!abandoned.get() && !failed.get()) try { segmenter.accept(samples) }
                    catch (e: Exception) { fail("语音处理失败：${e.message}") }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                fail("语音处理跟不上录音，已停止接收；请改用单模型后重试")
            }
        }
    }

    private fun endRegion(id: Long, samples: FloatArray, boundary: SpeechBoundary) {
        onRegion(id, samples.size, boundary)
        synchronized(outputLock) { ended[id] = boundary }
        if (engine.isStreaming) publish(id, engine.finishStream(), boundary, confirmed = !engine.hasRefinement)
        if (engine.hasRefinement || !engine.isStreaming) {
            synchronized(outputLock) { pending.add(id) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            try {
                refinement.execute {
                    if (abandoned.get()) return@execute
                    try {
                        check(System.nanoTime() < deadline) { "第二遍识别积压" }
                        val text = engine.refine(samples)
                        check(System.nanoTime() < deadline) { "第二遍识别超时" }
                        synchronized(outputLock) { pending.remove(id); publish(id, text, boundary, confirmed = true) }
                    } catch (e: Exception) {
                        synchronized(outputLock) { pending.remove(id); publish(id, "", boundary) }
                        fail("多语定稿失败：${e.message}；未将中英预览作为最终文本")
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                synchronized(outputLock) { pending.remove(id); publish(id, "", boundary) }
                fail("第二遍识别队列已满，请关闭 SenseVoice 二次校正后重试")
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

    /** Drains actual audio, then waits only for outstanding work; no artificial stop delay. */
    fun finish(timeoutMs: Long = 7000): String {
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

    fun cancel() {
        abandoned.set(true)
        synchronized(lifecycle) { accepting = false; audio.queue.clear(); audio.shutdown() }
        refinement.queue.clear(); refinement.shutdown()
    }
    fun awaitIdle() {
        while (!audio.awaitTermination(1, TimeUnit.SECONDS)) { }
        while (!refinement.awaitTermination(1, TimeUnit.SECONDS)) { }
        engine.reset()
    }
    val isIdle: Boolean get() = audio.isTerminated && refinement.isTerminated
}
