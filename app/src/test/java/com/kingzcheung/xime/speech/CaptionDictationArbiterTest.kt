package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keyboard mic wins over running captions; automatic caption starts wait for dictation. */
class CaptionDictationArbiterTest {
    private class Caption(val name: String)
    private val engine = ResidentSpeechSessionOwner { it() }
    private val arbiter = CaptionDictationArbiter<Caption>(engine) { it }

    @Test fun `tapping the keyboard mic puts running captions on hold, they resume after dictation`() {
        val page = Caption("page")
        assertTrue(arbiter.claimCaption(page))
        assertSame(page, arbiter.captionOwner)

        assertSame(page, arbiter.dictationStarting())   // the caption gives way
        assertNull(arbiter.captionOwner)
        assertTrue(arbiter.isHeld(page))
        assertNull("not while dictation is starting", arbiter.takeResumable())
        arbiter.dictationStartDone()

        assertSame(page, arbiter.takeResumable())        // dictation over, engine idle
        assertFalse(arbiter.isHeld(page))
        assertNull(arbiter.takeResumable())              // told once
    }

    @Test fun `mic tap on the caption page reply field starts dictation over listening captions`() {
        val page = Caption("page")
        assertTrue(arbiter.claimCaption(page))
        assertTrue(engine.start("k", { FakeEngine() }, { FakeSession() }, owner = page))
        val dictation = Any()
        val yielded = arbiter.dictationStarting()
        assertSame(page, yielded)
        engine.cancel(page)                               // the service cancels the yielded caption session
        assertTrue("dictation is not refused", engine.start("k", { FakeEngine() }, { FakeSession() }, owner = dictation))
        arbiter.dictationStartDone()
        assertNull("captions wait while dictation runs", arbiter.takeResumable())
        assertTrue(engine.cancel(dictation))
        assertSame(page, arbiter.takeResumable())
    }

    @Test fun `call captions starting during dictation wait instead of interrupting it`() {
        val dictation = Any()
        val engineUser = FakeEngine()
        assertTrue(engine.start("k", { engineUser }, { FakeSession() }, owner = dictation))
        val call = Caption("call")
        assertFalse(arbiter.claimCaption(call))
        assertTrue(arbiter.isHeld(call))
        assertNull("dictation still running", arbiter.takeResumable())
        assertTrue(engine.cancel(dictation))
        assertSame(call, arbiter.takeResumable())
    }

    @Test fun `a caption claimed during a dictation start waits too`() {
        assertNull(arbiter.dictationStarting())
        val call = Caption("call")
        assertFalse(arbiter.claimCaption(call))
        arbiter.dictationStartDone()
        assertSame(call, arbiter.takeResumable())
    }

    @Test fun `closing a waiting caption forgets it, and a new caption still replaces an old one`() {
        val held = Caption("held")
        assertNull(arbiter.dictationStarting())
        assertFalse(arbiter.claimCaption(held))
        arbiter.dictationStartDone()
        arbiter.dropCaption(held)
        assertNull(arbiter.takeResumable())

        val old = Caption("old"); val new = Caption("new")
        assertTrue(arbiter.claimCaption(old))
        assertTrue(arbiter.claimCaption(new))
        assertEquals(new, arbiter.captionOwner)
    }

    private class FakeEngine : LocalSpeechEngine {
        override val hasRefinement = false
        override val isStreaming = false
        override fun detector() = object : SpeechDetector {
            override fun speech(frame: FloatArray) = false
            override fun reset() { }
        }
        override fun accept(samples: FloatArray) = ""
        override fun finishStream() = ""
        override fun refine(samples: FloatArray) = ""
        override fun reset() { }
        override fun close() { }
    }

    private class FakeSession : ResidentSession {
        override fun acceptPcm(bytes: ByteArray) { }
        override fun finish() = ""
        override fun cancel() { }
        override fun awaitIdle() { }
        override val isIdle = true
    }
}
