package com.kingzcheung.xime.speech

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioRecord
import android.os.Handler
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs the real recording worker with a fake microphone and explicitly delayed backend shutdown. */
class SpeechRecognitionLanguageSessionTest {
    private data class Callbacks(val final: (String) -> Unit, val partial: ((String) -> Unit)?,
        val state: (RecognitionState) -> Unit, val error: (String) -> Unit)

    private class Backend : AsrBackend {
        override val name = "language session test"
        lateinit var callbacks: Callbacks
        val sessions = LinkedBlockingQueue<Pair<InputLanguage, Callbacks>>()
        val closing = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        override fun setCallbacks(onResult: (String) -> Unit, onPartialResult: ((String) -> Unit)?,
            onStateChange: (RecognitionState) -> Unit, onError: (String) -> Unit) {
            callbacks = Callbacks(onResult, onPartialResult, onStateChange, onError)
        }
        override fun initialize(): Boolean {
            callbacks.final("warmup must not enter editor")
            return true
        }
        override fun start(): Boolean = error("The product language must reach the backend")
        override fun start(language: InputLanguage): Boolean {
            sessions.put(language to callbacks)
            return true
        }
        override fun processAudioChunk(buffer: ByteArray) {}
        override fun stop() {
            closing.countDown()
            check(allowClose.await(5, TimeUnit.SECONDS))
            callbacks.final("closing result")
        }
        override fun cancel() = stop() // Deliberately emits even after cancellation: manager must reject it.
        override fun release() {}
        override fun isAvailable() = true
    }

    private class Fixture : AutoCloseable {
        val queue = LinkedBlockingQueue<Runnable>()
        val backend = Backend()
        val results = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var allocations = 0
        val manager: SpeechRecognitionManager
        init {
            val context = mock<Context>()
            val prefs = mock<SharedPreferences>()
            whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
            whenever(prefs.getBoolean(any(), any())).thenAnswer { it.getArgument(1) }
            whenever(prefs.getBoolean(eq(SettingsPreferences.KEY_STT_KEEP_ENGINE_ALIVE), any())).thenReturn(true)
            val handler = mock<Handler>()
            whenever(handler.post(any())).thenAnswer { queue.put(it.getArgument(0)); true }
            manager = SpeechRecognitionManager(context, { backend }, handler) {
                allocations++
                val released = CountDownLatch(1)
                mock<AudioRecord>().also { record ->
                    whenever(record.read(any<ShortArray>(), any(), any())).thenAnswer {
                        check(released.await(5, TimeUnit.SECONDS)); -1
                    }
                    doAnswer { released.countDown(); null }.whenever(record).release()
                }
            }
            manager.setCallbacks(onResult = { results += it }, onPartialResult = { results += it },
                onStateChange = {}, onError = { text, _ -> errors += text })
            assertTrue(manager.preload())
        }
        fun drain() { while (true) (queue.poll() ?: return).run() }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!condition() && System.nanoTime() < deadline) queue.poll(20, TimeUnit.MILLISECONDS)?.run()
            drain()
            assertTrue("Timed out waiting for recording lifecycle", condition())
        }
        fun session(): Pair<InputLanguage, Callbacks> {
            await { backend.sessions.isNotEmpty() }
            return backend.sessions.remove()
        }
        override fun close() {
            manager.cancelRecognition()
            backend.allowClose.countDown()
            manager.release()
            drain()
        }
    }

    @Test fun cancelledLanguageCannotWriteIntoQueuedNewLanguage() = Fixture().use { f ->
        f.manager.startRecognition(InputLanguage.CHINESE)
        val old = f.session()
        assertEquals(InputLanguage.CHINESE, old.first)
        old.second.partial?.invoke("queued old partial")
        f.manager.cancelRecognition()
        assertTrue(f.backend.closing.await(3, TimeUnit.SECONDS))
        f.manager.startRecognition(InputLanguage.JAPANESE)
        f.drain()
        assertTrue("Cannot start while old backend is closing", f.backend.sessions.isEmpty())
        assertTrue(f.results.isEmpty())
        f.backend.allowClose.countDown()
        val next = f.session()
        assertEquals(InputLanguage.JAPANESE, next.first)
        f.await { f.manager.getState() == RecognitionState.LISTENING }
        old.second.final("late old final")
        old.second.error("late old error")
        old.second.state(RecognitionState.ERROR)
        next.second.partial?.invoke("日本語")
        f.drain()
        assertEquals(listOf("日本語"), f.results)
        assertTrue(f.errors.isEmpty())
        assertEquals(RecognitionState.LISTENING, f.manager.getState())
    }

    @Test fun restartingDuringNormalStopDiscardsPreviousResultAndWaitsForShutdown() = Fixture().use { f ->
        f.manager.startRecognition(InputLanguage.ENGLISH)
        f.session()
        f.manager.stopRecognition()
        assertTrue(f.backend.closing.await(3, TimeUnit.SECONDS))
        f.manager.startRecognition(InputLanguage.JAPANESE)
        assertTrue(f.backend.sessions.isEmpty())
        f.backend.allowClose.countDown()
        assertEquals(InputLanguage.JAPANESE, f.session().first)
        assertTrue(f.results.isEmpty())
    }

    @Test fun cancelledQueuedStartNeverOpensAnotherMicrophone() = Fixture().use { f ->
        f.manager.startRecognition(InputLanguage.ENGLISH)
        f.session()
        f.manager.cancelRecognition()
        assertTrue(f.backend.closing.await(3, TimeUnit.SECONDS))
        f.manager.startRecognition(InputLanguage.JAPANESE)
        f.manager.cancelRecognition()
        f.backend.allowClose.countDown()
        f.await { f.manager.getState() == RecognitionState.IDLE }
        assertTrue(f.backend.sessions.isEmpty())
        assertEquals(1, f.allocations)
        assertTrue(f.results.isEmpty())
    }

    @Test fun activeSessionKeepsItsLanguageAndWarmupNeverWritesText() = Fixture().use { f ->
        f.manager.startRecognition(InputLanguage.ENGLISH)
        val active = f.session()
        f.manager.startRecognition(InputLanguage.JAPANESE)
        active.second.final("English")
        f.drain()
        assertEquals(InputLanguage.ENGLISH, active.first)
        assertTrue(f.backend.sessions.isEmpty())
        assertEquals(listOf("English"), f.results)
    }

    @Test fun unknownLanguageIsRejectedBeforeMicrophoneAllocation() = Fixture().use { f ->
        f.manager.startRecognition(InputLanguage.UNSPECIFIED)
        f.drain()
        assertEquals(0, f.allocations)
        assertTrue(f.backend.sessions.isEmpty())
        assertEquals(RecognitionState.ERROR, f.manager.getState())
        assertTrue(f.errors.single().contains("未标注语言"))
        assertTrue(f.results.isEmpty())
    }
}
