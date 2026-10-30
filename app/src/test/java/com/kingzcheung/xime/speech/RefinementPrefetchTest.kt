package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The streaming model shows words while SenseVoice / X-ASR is still loading on the refinement worker. */
class RefinementPrefetchTest {
    private class Engine(override val refinementLoaded: Boolean, val loading: CountDownLatch = CountDownLatch(0)) : LocalSpeechEngine {
        override val hasRefinement = true
        override val isStreaming = true
        @Volatile var prepared = 0
        @Volatile var preparedBeforeRefine = false
        override fun detector(): SpeechDetector = object : SpeechDetector {
            override fun speech(frame: FloatArray) = frame.any { kotlin.math.abs(it) > .03f }
            override fun reset() { }
        }
        override fun prepareRefinement() { loading.await(5, TimeUnit.SECONDS); prepared++ }
        override fun accept(samples: FloatArray) = "预览"
        override fun finishStream() = "预览"
        override fun refine(samples: FloatArray): String { preparedBeforeRefine = prepared == 1; return "定稿" }
        override fun reset() { }
        override fun close() { }
    }
    private fun pcm(samples: Int, value: Int) = ByteArray(samples * 2) { if (it % 2 == 0) value.toByte() else (value shr 8).toByte() }

    @Test fun `streaming text appears while the second pass is still loading`() {
        val loading = CountDownLatch(1)
        val engine = Engine(refinementLoaded = false, loading)
        val shown = CountDownLatch(1)
        val s = LocalSpeechSession(engine, { if (it.isNotEmpty()) shown.countDown() }, {})
        s.acceptPcm(pcm(16000, 1600))
        assertTrue("preview must not wait for the second-pass load", shown.await(2, TimeUnit.SECONDS))
        assertEquals(0, engine.prepared)
        loading.countDown()
        assertEquals("定稿", s.finish())
        assertEquals(1, engine.prepared)
        assertTrue("the load runs before the first final on the same worker", engine.preparedBeforeRefine)
    }

    @Test fun `an engine with the second pass already loaded is not asked again`() {
        val engine = Engine(refinementLoaded = true)
        LocalSpeechSession(engine, {}, {}).apply { acceptPcm(pcm(16000, 1600)); finish() }
        assertEquals(0, engine.prepared)
    }
}
