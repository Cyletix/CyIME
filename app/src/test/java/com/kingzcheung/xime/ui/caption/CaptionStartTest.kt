package com.kingzcheung.xime.ui.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The caption microphone may only be open for the session that is still current. */
class CaptionStartTest {
    private class FakeMic {
        var open = false
        var opens = 0
        fun start(): Boolean { opens++; open = true; return true }
        fun stop() { open = false }
    }

    @Test fun `closing the page while the model loads never opens the microphone`() {
        var epoch = 1L
        val mine = epoch
        val mic = FakeMic()
        epoch++                      // exit() on the main thread while client.start() was loading
        mic.stop()
        assertFalse(startUnlessStale({ mine == epoch }, mic::start, mic::stop))
        assertFalse(mic.open)
        assertEquals(0, mic.opens)
    }

    @Test fun `closing during the open itself closes the microphone again`() {
        var epoch = 1L
        val mine = epoch
        val mic = FakeMic()
        val started = startUnlessStale({ mine == epoch }, { mic.start().also { epoch++; mic.stop() } }, mic::stop)
        assertFalse(started)
        assertFalse(mic.open)
    }

    @Test fun `switching language while loading leaves only the new session's microphone`() {
        var epoch = 1L
        val mic = FakeMic()
        val old = epoch
        epoch++                      // chooseLanguage(): pause() + start() during Starting
        val new = epoch
        // The worker runs the old start first, then the new one.
        assertFalse(startUnlessStale({ old == epoch }, mic::start, mic::stop))
        assertFalse(mic.open)
        assertTrue(startUnlessStale({ new == epoch }, mic::start, mic::stop))
        assertTrue(mic.open)
        assertEquals(1, mic.opens)
    }
}
