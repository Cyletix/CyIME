package com.kingzcheung.xime.speech

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real AudioRecord, deterministic backend: audio is discarded, never saved or sent. */
class MicrophoneReleaseTest {
    @get:Rule val activity = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val permission = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun ui(block: () -> Unit) = activity.runOnUiThread(block)
    private fun await(condition: () -> Boolean) = activity.waitUntil(5000, condition)
    private fun idle() = audio.activeRecordingConfigurations.isEmpty()

    private class Backend(val begin: () -> Boolean = { true }, val finish: () -> Unit = {}) : AsrBackend {
        override val name = "microphone lifecycle probe"
        val releases = AtomicInteger()
        val starts = AtomicInteger()
        override fun initialize() = true
        override fun start(): Boolean { starts.incrementAndGet(); return begin() }
        override fun processAudioChunk(buffer: ByteArray) { } // discard immediately
        override fun stop() = finish()
        override fun cancel() { }
        override fun release() { releases.incrementAndGet() }
        override fun isAvailable() = true
        override fun setCallbacks(onResult: (String) -> Unit, onPartialResult: ((String) -> Unit)?,
            onStateChange: (RecognitionState) -> Unit, onError: (String) -> Unit) { }
    }

    private fun probe(backend: Backend = Backend(), run: (SpeechRecognitionManager, Backend) -> Unit) {
        assumeTrue("Do not interrupt another app's recording or call", idle() && audio.mode == AudioManager.MODE_NORMAL)
        val keep = SettingsPreferences.isSttKeepEngineAlive(context)
        SettingsPreferences.setSttKeepEngineAlive(context, true)
        val manager = SpeechRecognitionManager(context, backendFactory = { backend })
        try { run(manager, backend) }
        finally {
            ui { manager.release() }
            SettingsPreferences.setSttKeepEngineAlive(context, keep)
            await { idle() }
        }
    }

    @Test fun stoppingPreStartWithoutRecognitionReleasesMicrophone() = probe { manager, _ ->
        ui { manager.startPreStart() }; await { !idle() }
        ui { manager.stopRecognition() }; await { idle() }
        ui { manager.startPreStart() }; await { !idle() }
        ui { manager.cancelRecognition() }; await { idle() }
    }

    @Test fun failingEngineStartupReleasesPreStartedMicrophone() = probe(Backend(begin = { error("startup failed") })) { manager, backend ->
        ui { manager.startPreStart(); manager.startRecognition() }
        await { backend.starts.get() == 1 }
        await { manager.getState() == RecognitionState.ERROR && idle() }
    }

    @Test fun cancellingBlockedStartupReleasesBeforeBackendReturns() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        try {
            probe(Backend(begin = { entered.countDown(); check(resume.await(15, TimeUnit.SECONDS)); true })) { manager, _ ->
                ui { manager.startPreStart(); manager.startRecognition() }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                await { !idle() }
                ui { manager.cancelRecognition() }
                await { idle() }
                resume.countDown()
                SystemClock.sleep(300)
                assertTrue("Late startup must not reacquire microphone", idle())
            }
        } finally { resume.countDown() }
    }

    @Test fun keepingModelAliveNeverKeepsMicrophoneAfterStop() = probe { manager, backend ->
        ui { manager.startRecognition() }
        await { manager.getState() == RecognitionState.LISTENING && !idle() }
        ui { manager.stopRecognition() }; await { idle() }
        SystemClock.sleep(4000)
        assertEquals("Backend can remain resident", 0, backend.releases.get())
        assertTrue("Resident backend must not own microphone", idle())
    }

    @Test fun throwingBackendShutdownCannotRetainMicrophone() = probe(Backend(finish = { error("shutdown failed") })) { manager, _ ->
        ui { manager.startRecognition() }
        await { manager.getState() == RecognitionState.LISTENING && !idle() }
        ui { manager.stopRecognition() }; await { idle() }
    }
}
