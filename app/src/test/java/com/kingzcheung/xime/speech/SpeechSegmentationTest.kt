package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeechSegmentationTest {
    private class Detector : SpeechDetector {
        override fun speech(frame: FloatArray) = frame.any { it != 0f }
        override fun reset() { }
    }
    @Test fun `continuous speech has bounded regions without duplicated or missing samples`() {
        val sent = ArrayList<Float>()
        val closed = ArrayList<Float>()
        val boundaries = ArrayList<SpeechBoundary>()
        val s = SpeechSegmenter(Detector(), 1000, { _, a -> sent.addAll(a.toList()) }, { _, a, b ->
            closed.addAll(a.toList()); boundaries.add(b)
        })
        val input = FloatArray(16000 * 11 + 117) { .05f + (it % 17) * .0001f }
        input.asList().chunked(937).forEach { s.accept(it.toFloatArray()) }
        s.finishInput()
        assertEquals(input.toList(), sent)
        assertEquals(sent, closed)
        assertTrue(boundaries.count { it == SpeechBoundary.LIMIT } >= 3)
        assertEquals(SpeechBoundary.STOP, boundaries.last())
    }
    @Test fun `natural pause ends a region and short final PCM is retained`() {
        val regions = mutableListOf<Pair<FloatArray, SpeechBoundary>>()
        val s = SpeechSegmenter(Detector(), 12000, { _, _ -> }, { _, a, b -> regions.add(a to b) })
        s.accept(FloatArray(16000) { .1f }); s.accept(FloatArray(16000))
        s.accept(FloatArray(1117) { .2f }); s.finishInput()
        assertEquals(2, regions.size)
        assertEquals(SpeechBoundary.PAUSE, regions[0].second)
        assertEquals(1117, regions[1].first.count { it == .2f })
        assertEquals(16000, regions[0].first.count { it == .1f })
    }
    @Test fun `silence does not become a phrase`() {
        var count = 0
        val s = SpeechSegmenter(Detector(), 4000, { _, _ -> count++ }, { _, _, _ -> count++ })
        s.accept(FloatArray(16000 * 20)); s.finishInput()
        assertEquals(0, count)
    }
    @Test fun `word boundaries and punctuation become one space without final punctuation`() {
        assertEquals("hello world 你好 再见", cleanSpeechText("<|zh|>hello world，你好。 再见！"))
    }
    @Test fun `late correction replaces its own region without repeating the next phrase`() {
        val t = SpeechTranscript()
        t.update(1, "错误预览", SpeechBoundary.PAUSE)
        t.update(2, "下一句话", SpeechBoundary.PAUSE)
        assertEquals("正确结果 下一句话", t.update(1, "正确结果", SpeechBoundary.PAUSE))
        assertEquals("正确结果 下一句话", t.update(2, "下一句话", SpeechBoundary.PAUSE))
    }
    @Test fun `forced window cut does not insert a fake Chinese word boundary`() {
        val t = SpeechTranscript()
        t.update(1, "都不会", SpeechBoundary.LIMIT)
        assertEquals("都不会打字了", t.update(2, "打字了", SpeechBoundary.PAUSE))
        assertEquals("都不会打字了 怎么", t.update(3, "怎么", SpeechBoundary.STOP))
    }
    @Test fun `empty final removes only the unsupported preview`() {
        val t = SpeechTranscript()
        t.update(1, "第一句", SpeechBoundary.PAUSE)
        t.update(2, "错误的日语预览", SpeechBoundary.PAUSE)
        assertEquals("第一句", t.update(2, ""))
    }

    @Test fun `SenseVoice token spaces do not replace actual pause separators`() {
        assertEquals("うちの中学は弁当制で 持っていけない場合は50円", cleanSenseVoiceText("うち の 中学 は 弁当 制 で、 持っ てい けない 場 合は 50 円。"))
        assertEquals("hello world 你好 世界", cleanSenseVoiceText("hello world，你 好，世界。"))
    }


    @Test fun `second pass and region assembly preserve decimals and dictated names until UI`() {
        val t = SpeechTranscript()
        t.update(1, "零点零五")
        val corrected = cleanSenseVoiceText("<|zh|>0.05 句 号。")
        assertEquals("0.05句号", corrected)
        assertEquals("0.05句号", t.update(1, corrected, SpeechBoundary.PAUSE))
        val result = t.update(2, "5-2=3，问号。", SpeechBoundary.STOP)
        assertEquals("0.05。5-2=3？", com.kingzcheung.xime.service.normalizeVoiceText(result))
        assertEquals(result, cleanSpeechText(result))
    }

    private open class FakeEngine(override val hasRefinement: Boolean = false) : LocalSpeechEngine {
        override val isStreaming = true
        var streamSamples = 0
        val finalizedSizes = mutableListOf<Int>()
        override fun detector(): SpeechDetector = Detector()
        override fun accept(samples: FloatArray): String { streamSamples += samples.size; return "预览" }
        override fun finishStream(): String { finalizedSizes.add(streamSamples); streamSamples = 0; return "第一遍" }
        override fun refine(samples: FloatArray) = "最终结果"
        override fun reset() { }
        override fun close() { }
    }
    private fun pcm(count: Int, sample: Int = 1600) = ByteArray(count * 2) { if (it % 2 == 0) sample.toByte() else (sample shr 8).toByte() }

    @Test fun `stop flushes partial frame and does not wait a fixed spinner interval`() {
        val engine = FakeEngine()
        val errors = mutableListOf<String>()
        val s = LocalSpeechSession(engine, {}, { errors.add(it) })
        s.acceptPcm(pcm(16000 + 73))
        assertEquals("第一遍", s.finish())
        assertEquals(listOf(16073), engine.finalizedSizes)
        assertTrue(errors.isEmpty()); assertTrue(s.isIdle)
    }
    @Test fun `slow second pass does not block capture or first pass`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = object : FakeEngine(true) {
            override fun refine(samples: FloatArray): String { entered.countDown(); release.await(2, TimeUnit.SECONDS); return "校正" }
        }
        val s = LocalSpeechSession(engine, {}, {})
        s.acceptPcm(pcm(16000)); s.acceptPcm(pcm(16000, 0))
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        s.acceptPcm(pcm(16000)); release.countDown()
        assertEquals("校正 校正", s.finish())
    }
    @Test fun `cancel rejects a late multilingual correction`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val output = java.util.Collections.synchronizedList(mutableListOf<String>())
        val engine = object : FakeEngine(true) {
            override fun refine(samples: FloatArray): String { entered.countDown(); release.await(2, TimeUnit.SECONDS); return "迟到" }
        }
        val s = LocalSpeechSession(engine, { output.add(it) }, {})
        s.acceptPcm(pcm(16000)); s.acceptPcm(pcm(16000, 0))
        assertTrue(entered.await(2, TimeUnit.SECONDS)); s.cancel(); val count = output.size
        release.countDown(); s.awaitIdle()
        assertEquals(count, output.size); assertFalse(output.contains("迟到"))
    }
    @Test fun `failed multilingual correction never promotes bilingual preview`() {
        val errors = mutableListOf<String>()
        val engine = object : FakeEngine(true) { override fun refine(samples: FloatArray): String = error("decode failed") }
        val s = LocalSpeechSession(engine, {}, { errors.add(it) })
        s.acceptPcm(pcm(16000))
        assertEquals("", s.finish()); assertEquals(1, errors.size)
    }
}
