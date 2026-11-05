package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** The gate sits between the streaming preview and both second-pass results (per pause and the end-of-input re-read). */
class SecondPassGateSessionTest {
    /** Single characters only; never saw 嵌: a second pass that writes 嵌 is put back. */
    private val gate = SecondPassGate(object : WordLanguageModel {
        override fun id(text: String) = if ("嵌" in text || text.length > 1) -1 else text.hashCode() and 0xffff
        override fun unigram(id: Int) = if (id < 0) -18.0 else -10.0
        override fun bigram(history: Int, word: Int) = unigram(word)
        override fun trigram(history2: Int, history: Int, word: Int) = unigram(word)
    })

    private open class Engine(private val chinese: Boolean = true) : LocalSpeechEngine {
        override val hasRefinement = true
        override val isStreaming = true
        override val lockedChinese get() = chinese
        override fun detector(): SpeechDetector = object : SpeechDetector {
            override fun speech(frame: FloatArray) = frame.any { kotlin.math.abs(it) > .03f }
            override fun reset() { }
        }
        override fun accept(samples: FloatArray) = "浅绿米灰这些"
        override fun finishStream() = "浅绿米灰这些"
        override fun refine(samples: FloatArray) = "嵌绿米灰这些"
        override fun refinePunctuated(samples: FloatArray) = "嵌绿米灰这些。"
        override fun reset() { }
        override fun close() { }
    }
    private fun pcm(samples: Int, value: Int) = ByteArray(samples * 2) { if (it % 2 == 0) value.toByte() else (value shr 8).toByte() }
    private fun speak(s: LocalSpeechSession) { s.acceptPcm(pcm(16000, 1600)); s.acceptPcm(pcm(32000, 0)) }

    @Test fun `the per-pause second pass keeps the preview's word when the model never saw the new one`() {
        val s = LocalSpeechSession(Engine(), {}, {}, gate = gate)
        speak(s)
        assertEquals("浅绿米灰这些", s.finish())
    }

    @Test fun `the end-of-input re-read is checked against the previews too`() {
        val s = LocalSpeechSession(Engine(), {}, {}, wholeSentence = true, gate = gate)
        speak(s)
        assertEquals("浅绿米灰这些。", s.finish())
    }

    @Test fun `without a gate the second pass replaces the preview as before`() {
        val s = LocalSpeechSession(Engine(), {}, {})
        speak(s)
        assertEquals("嵌绿米灰这些", s.finish())
    }

    @Test fun `a voice language other than Chinese is not checked against the Chinese model`() {
        val s = LocalSpeechSession(Engine(chinese = false), {}, {}, gate = gate)
        speak(s)
        assertEquals("嵌绿米灰这些", s.finish())
    }
}
