package com.kingzcheung.xime.speech

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Manager ownership tests. Real capture and native decoding have separate device suites. */
class SpeechRecognitionManagerIntegrationTest {
    private class Backend(val success: Boolean) : AsrBackend {
        val initializations = AtomicInteger()
        val releases = AtomicInteger()
        override val name = "preload probe"
        override fun initialize(): Boolean { initializations.incrementAndGet(); return success }
        override fun start() = error("Preload must not open an input session")
        override fun processAudioChunk(buffer: ByteArray) = error("No capture expected")
        override fun stop() { }
        override fun cancel() { }
        override fun release() { releases.incrementAndGet() }
        override fun isAvailable() = true
        override fun setCallbacks(onResult: (String) -> Unit, onPartialResult: ((String) -> Unit)?,
            onStateChange: (RecognitionState) -> Unit, onError: (String) -> Unit) { }
    }
    @Test fun repeatedPreloadReusesOneBackendAndReleasePreventsReload() {
        val backend = Backend(true)
        val manager = SpeechRecognitionManager(InstrumentationRegistry.getInstrumentation().targetContext, backendFactory = { backend })
        try {
            assertTrue(manager.preload())
            assertTrue(manager.preload())
            assertEquals(1, backend.initializations.get())
            assertEquals(RecognitionState.IDLE, manager.getState())
        } finally { manager.release() }
        assertFalse(manager.preload())
        assertEquals(1, backend.initializations.get())
    }
    @Test fun failedPreloadIsReleasedAndCanBeRetried() {
        val backend = Backend(false)
        val manager = SpeechRecognitionManager(InstrumentationRegistry.getInstrumentation().targetContext, backendFactory = { backend })
        try {
            repeat(2) { assertFalse(manager.preload()) }
            assertEquals(2, backend.initializations.get())
            assertEquals(2, backend.releases.get())
        } finally { manager.release() }
    }
}
