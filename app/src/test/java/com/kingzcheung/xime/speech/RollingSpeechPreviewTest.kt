package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.Collections

class RollingSpeechPreviewTest {
    private class Engine(val entered: CountDownLatch, val proceed: CountDownLatch) : LocalSpeechEngine {
        override val hasRefinement = false
        override val isStreaming = false
        override val hasRollingPreview = true
        override val maxSegmentMs = 8000
        override val useEnergyPause = false
        override fun detector() = object : SpeechDetector { override fun speech(frame: FloatArray) = true; override fun reset() {} }
        override fun accept(samples: FloatArray) = error("Not a streaming recognizer")
        override fun finishStream() = ""
        override fun preview(samples: FloatArray): String { entered.countDown(); check(proceed.await(3, TimeUnit.SECONDS)); return "今日は東京" }
        override fun refine(samples: FloatArray) = "今日は東京 明日は大阪"
        override fun reset() {}
        override fun close() {}
    }
    @Test fun aSingleCaptureChunkCanDisplayWithoutWaitingForTwoSecondsOrAnEndpoint() {
        val entered = CountDownLatch(1); val proceed = CountDownLatch(1); val visible = CountDownLatch(1)
        val texts = Collections.synchronizedList(mutableListOf<String>())
        val session = LocalSpeechSession(Engine(entered, proceed), { texts.add(it); visible.countDown() }, { error(it) })
        try {
            session.acceptPcm(ByteArray(3200) { 1 }) // One 100 ms capture chunk, no endpoint.
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            proceed.countDown()
            assertTrue("Must display before stop or pause", visible.await(2, TimeUnit.SECONDS))
            assertEquals("今日は東京", texts.first())
            assertEquals("今日は東京 明日は大阪", session.finish())
            assertEquals("今日は東京 明日は大阪", texts.last())
        } finally { proceed.countDown(); session.cancel(); session.awaitIdle() }
    }
    @Test fun anEndedRegionNeverPublishesAnOldPreviewOverItsFinal() {
        val entered = CountDownLatch(1); val proceed = CountDownLatch(1); val ended = CountDownLatch(1)
        val texts = Collections.synchronizedList(mutableListOf<String>())
        val session = LocalSpeechSession(Engine(entered, proceed), { texts.add(it) }, { error(it) }, { _, _, _ -> ended.countDown() })
        val stopper = Executors.newSingleThreadExecutor()
        try {
            session.acceptPcm(ByteArray(65024) { 1 }); assertTrue(entered.await(2, TimeUnit.SECONDS))
            val final = stopper.submit<String> { session.finish() }
            assertTrue(ended.await(2, TimeUnit.SECONDS)); proceed.countDown()
            assertEquals("今日は東京 明日は大阪", final.get(3, TimeUnit.SECONDS))
            assertEquals(listOf("今日は東京 明日は大阪"), texts.toList())
        } finally { proceed.countDown(); session.cancel(); session.awaitIdle(); stopper.shutdownNow() }
    }
}
