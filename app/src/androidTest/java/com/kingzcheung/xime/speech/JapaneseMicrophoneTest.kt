package com.kingzcheung.xime.speech

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Explicit opt-in only: a bounded live microphone run through the actual app manager and :asr service. */
class JapaneseMicrophoneTest {
    @get:Rule val activity = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val permission = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)
    @Test fun recognizeLiveJapaneseAndReleaseMicrophone() {
        assumeTrue("Live capture requires explicit test opt-in", InstrumentationRegistry.getArguments().getString("liveMicrophone") == "true")
        val context = activity.activity
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assumeTrue("Do not interrupt another recording or call", audio.activeRecordingConfigurations.isEmpty() && audio.mode == AudioManager.MODE_NORMAL)
        assertEquals(SpeechModelCatalog.ZIPFORMER_JA, AsrModelManager(context).selectionForLanguage(InputLanguage.JAPANESE).mode)
        val pcm = ByteArrayOutputStream()
        val backend = checkNotNull(AsrSupport.create(context))
        val observedBackend = object : AsrBackend by backend {
            override fun processAudioChunk(buffer: ByteArray) {
                pcm.write(buffer)
                backend.processAudioChunk(buffer)
            }
        }
        val manager = SpeechRecognitionManager(context, backendFactory = { observedBackend })
        val result = AtomicReference("")
        val done = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val label = mutableStateOf("日语麦克风测试：点击开始后说话")
        val preview = mutableStateOf("")
        val startRequested = mutableStateOf(false)
        val maxAmplitude = AtomicReference(0f)
        val recordingStates = org.json.JSONArray()
        activity.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text(label.value, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                Text("请说日语，句子之间稍作停顿。录音会自动停止。")
                Text("请留在这个测试页，避免其他应用占用麦克风。")
                Button(onClick = { startRequested.value = true }, enabled = !startRequested.value) { Text("开始 15 秒录音") }
                Spacer(Modifier.height(24.dp))
                Text(preview.value, style = MaterialTheme.typography.bodyLarge)
            }
        } } }
        try {
            activity.waitUntil(120000) { startRequested.value }
            activity.runOnUiThread {
                manager.setCallbacks(onResult = { result.set(it); preview.value = it; done.countDown() },
                    onPartialResult = { preview.value = it }, onStateChange = {},
                    onError = { message, _ -> errors.add(message); done.countDown() },
                    onAmplitude = { maxAmplitude.set(maxOf(maxAmplitude.get(), it)) })
                manager.startRecognition(InputLanguage.JAPANESE)
            }
            activity.waitUntil(15000) { audio.activeRecordingConfigurations.isNotEmpty() || errors.isNotEmpty() }
            assertTrue(errors.toString(), errors.isEmpty())
            for (seconds in 15 downTo 1) {
                activity.runOnUiThread { label.value = "正在录音 · 还剩 $seconds 秒" }
                audio.activeRecordingConfigurations.forEach { config ->
                    recordingStates.put(JSONObject().put("remaining_seconds", seconds)
                        .put("silenced", config.isClientSilenced).put("source", config.clientAudioSource)
                        .put("sample_rate", config.clientFormat.sampleRate).put("device_type", config.audioDevice?.type))
                }
                SystemClock.sleep(1000)
            }
            val stopAt = System.nanoTime()
            activity.runOnUiThread { label.value = "录音已停止，正在识别"; manager.stopRecognition() }
            activity.waitUntil(3000) { audio.activeRecordingConfigurations.isEmpty() }
            assertTrue("No final result", done.await(20, TimeUnit.SECONDS))
            val elapsed = (System.nanoTime() - stopAt) / 1e6
            activity.runOnUiThread { label.value = "测试结束 · 麦克风已释放" }
            val output = File(context.getExternalFilesDir(null), "speech-eval/zipformer-ja/microphone.json")
            output.parentFile?.mkdirs()
            val captured = pcm.toByteArray()
            File(output.parentFile, "microphone.pcm").writeBytes(captured)
            output.writeText(JSONObject().put("source", "phone_microphone").put("capture_seconds", 15)
                .put("text", result.get()).put("errors", org.json.JSONArray(errors.toList()))
                .put("max_amplitude", maxAmplitude.get())
                .put("pcm_bytes_delivered", captured.size).put("recording_states", recordingStates)
                .put("stop_ms", elapsed).put("microphone_released", audio.activeRecordingConfigurations.isEmpty()).toString(2))
            assertTrue(errors.toString(), errors.isEmpty())
            // Ambient silence is allowed; speech quality must be assessed against what was actually spoken.
        } finally {
            activity.runOnUiThread { manager.release() }
            activity.waitUntil(3000) { audio.activeRecordingConfigurations.isEmpty() }
        }
    }
}
