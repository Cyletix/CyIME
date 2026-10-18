package com.kingzcheung.xime.speech

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.AsrInferenceClient
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import kotlin.system.measureTimeMillis

/** Public WAVs only. Uses installed models, never copies over user files or opens a microphone. */
class LocalSpeechServiceTest {
    @Test fun japaneseEnglishJapaneseUsesSupportedModelsAndRecognizesRecordedSpeech() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "speech-eval")
        val manager = AsrModelManager(context)
        val prefs = context.getSharedPreferences("asr_model", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("selected_model_en", null)
        val report = JSONObject().put("previous_english_selection", previous ?: JSONObject.NULL)
        val results = JSONArray()
        report.put("results", results)
        val backend = OfflineAsrBackend(context)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val result = java.util.concurrent.atomic.AtomicReference("")
        var passed = false
        try {
            assertTrue("Configured English model must be installed", manager.selectionForLanguage(InputLanguage.ENGLISH).ready)
            backend.setCallbacks({ result.set(it) }, {}, {}, { errors.add(it) })
            assertTrue(backend.prepareForRecording(InputLanguage.JAPANESE))
            for (language in listOf(InputLanguage.JAPANESE, InputLanguage.ENGLISH, InputLanguage.JAPANESE)) {
                result.set("")
                val selected = manager.selectionForLanguage(language)
                assertTrue(SpeechLanguages.supports(selected.mode, language))
                val row = JSONObject().put("language", language.id).put("mode", selected.mode)
                results.put(row)
                row.put("load_ms", measureTimeMillis { assertTrue(backend.start(language)) })
                val pcm = readPcmWav(File(root, "switch-${language.id}.wav").readBytes())
                for (offset in pcm.indices step 3200) {
                    backend.processAudioChunk(pcm.copyOfRange(offset, minOf(offset + 3200, pcm.size)))
                    Thread.sleep(100)
                }
                row.put("finish_ms", measureTimeMillis { backend.stop() }).put("text", result.get())
                assertTrue(errors.toString(), errors.isEmpty())
                assertTrue("No speech for ${language.id}", result.get().isNotBlank())
                if (language == InputLanguage.ENGLISH) {
                    val expected = File(root, "switch-en-keywords.txt").readText().trim().split('\n')
                    expected.forEach { assertTrue("Missing $it in ${result.get()}", result.get().contains(it.trim(), ignoreCase = true)) }
                } else assertTrue(result.get(), result.get().any { it in '\u3040'..'\u30ff' })
            }
            passed = true
        } finally {
            backend.releaseModel()
            assertEquals("Device check must preserve the user's English model choice", previous, prefs.getString("selected_model_en", null))
            report.put("passed", passed).put("configuration_changed", false).put("errors", JSONArray(errors.toList()))
            File(root, "language-switch-results.json").writeText(report.toString(2))
        }
    }

    @Test fun turningCorrectionOffStillRecognizesThroughConfiguredBackend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("asr_model", android.content.Context.MODE_PRIVATE)
        val key = "selected_model_zh"
        val previous = prefs.getString(key, null)
        val manager = AsrModelManager(context)
        val root = File(context.getExternalFilesDir(null), "speech-eval")
        val pcm = readPcmWav(File(root, "zh.wav").readBytes())
        val reports = JSONArray()
        try {
            for (mode in listOf(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.PARAFORMER)) {
                manager.setModel(mode, InputLanguage.CHINESE)
                manager.setRefinementEnabled(true, InputLanguage.CHINESE)
                manager.setRefinementEnabled(false, InputLanguage.CHINESE)
                val backend = OfflineAsrBackend(context)
                val result = java.util.concurrent.atomic.AtomicReference("")
                val errors = Collections.synchronizedList(mutableListOf<String>())
                backend.setCallbacks({ result.set(it) }, {}, {}, { errors.add(it) })
                try {
                    assertEquals(mode, AsrModelManager(context).selectionForLanguage(InputLanguage.CHINESE).mode)
                    assertTrue(backend.prepareForRecording(InputLanguage.CHINESE))
                    assertTrue(backend.start(InputLanguage.CHINESE))
                    for (offset in pcm.indices step 3200) {
                        backend.processAudioChunk(pcm.copyOfRange(offset, minOf(offset + 3200, pcm.size)))
                        Thread.sleep(100)
                    }
                    backend.stop()
                    reports.put(JSONObject().put("mode", mode).put("text", result.get()).put("errors", JSONArray(errors.toList())))
                    File(root, "configured-standalone-results.json").writeText(reports.toString(2))
                    assertTrue(errors.toString(), errors.isEmpty())
                    assertTrue("No configured recognition: $mode", result.get().isNotBlank())
                } finally { backend.releaseModel() }
            }
        } finally {
            prefs.edit().apply { if (previous == null) remove(key) else putString(key, previous) }.commit()
        }
    }

    @Test fun standaloneChineseModelsRecognizeWithoutSenseVoice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "speech-eval")
        val pcm = readPcmWav(File(root, "zh.wav").readBytes())
        val reports = JSONArray()
        val failures = mutableListOf<String>()
        val client = AsrInferenceClient(context)
        val manager = AsrModelManager(context)
        try {
            assertTrue(client.ensureBound())
            for (mode in listOf(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.PARAFORMER)) {
                val selection = manager.selection(mode, InputLanguage.CHINESE)
                val errors = Collections.synchronizedList(mutableListOf<String>())
                val updates = Collections.synchronizedList(mutableListOf<String>())
                val row = JSONObject().put("model", mode).put("ready", selection.ready)
                    .put("has_second_model", selection.secondDir != null)
                    .put("files", JSONArray(selection.first!!.files.map { name ->
                        JSONObject().put("name", name).put("bytes", File(selection.firstDir, name).length())
                    }))
                reports.put(row)
                val callback = object : AsrInferenceClient.AsrCallback {
                    override fun onPartialResult(text: String) { updates.add(text) }
                    override fun onFinalResult(text: String) { updates.add(text) }
                    override fun onError(message: String) { errors.add(message) }
                }
                try {
                    assertNull(selection.secondDir)
                    assertTrue("Model not installed: $mode", selection.ready)
                    row.put("start_ms", measureTimeMillis { assertTrue(client.startAsr(mode, callback, InputLanguage.CHINESE)) })
                    for (offset in pcm.indices step 3200) {
                        client.pushAsrAudio(pcm.copyOfRange(offset, minOf(offset + 3200, pcm.size)))
                        delay(100)
                    }
                    var text = ""
                    row.put("stop_ms", measureTimeMillis { text = client.stopAsr() }).put("text", text)
                    row.put("updates", JSONArray(updates.toList())).put("errors", JSONArray(errors.toList()))
                    assertTrue(errors.toString(), errors.isEmpty())
                    assertTrue("No standalone recognition: $mode", text.isNotBlank())
                } catch (failure: Throwable) {
                    row.put("failure", failure.toString())
                    failures.add("$mode: $failure")
                    client.cancelAsr()
                } finally {
                    File(root, "standalone-results.json").writeText(reports.toString(2))
                }
            }
        } finally { client.releaseAsr(); client.unbind() }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun delayedManagerReleaseKeepsNewRecordingUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backend = OfflineAsrBackend(context)
        val result = java.util.concurrent.atomic.AtomicReference("")
        val errors = Collections.synchronizedList(mutableListOf<String>())
        backend.setCallbacks({ result.set(it) }, {}, {}, { errors.add(it) })
        try {
            assertTrue(backend.prepareForRecording(InputLanguage.JAPANESE))
            assertTrue(backend.start(InputLanguage.JAPANESE))
            backend.release() // A retired manager's delayed cleanup of the shared instance.
            val pcm = readPcmWav(File(context.getExternalFilesDir(null), "speech-eval/ja.wav").readBytes())
            for (offset in pcm.indices step 3200) {
                backend.processAudioChunk(pcm.copyOfRange(offset, (offset + 3200).coerceAtMost(pcm.size)))
                Thread.sleep(100)
            }
            backend.stop()
            assertTrue(errors.toString(), errors.isEmpty())
            assertTrue(result.get(), result.get().any { it in '\u3040'..'\u30ff' })
        } finally { backend.releaseModel() }
    }

    @Test fun retiredConnectionCannotStopOrFeedANewerRecording() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val older = AsrInferenceClient(context)
        val current = AsrInferenceClient(context)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val callback = object : AsrInferenceClient.AsrCallback {
            override fun onPartialResult(text: String) {}
            override fun onFinalResult(text: String) {}
            override fun onError(message: String) { errors.add(message) }
        }
        try {
            assertTrue(older.startAsr(SpeechModelCatalog.SENSEVOICE, callback, InputLanguage.JAPANESE))
            // The newer recording supersedes an old connection still holding its session.
            assertTrue(current.startAsr(SpeechModelCatalog.SENSEVOICE, callback, InputLanguage.JAPANESE))
            assertThrows(IllegalStateException::class.java) { older.pushAsrAudio(ByteArray(3200)) }
            older.stopAsr()
            older.cancelAsr()
            older.releaseAsr()
            val pcm = readPcmWav(File(context.getExternalFilesDir(null), "speech-eval/ja.wav").readBytes())
            for (offset in pcm.indices step 3200) {
                current.pushAsrAudio(pcm.copyOfRange(offset, (offset + 3200).coerceAtMost(pcm.size)))
                delay(100)
            }
            val result = current.stopAsr()
            assertTrue(errors.toString(), errors.isEmpty())
            assertTrue("Old connection erased the active recording: $result", result.any { it in '\u3040'..'\u30ff' })
        } finally {
            older.unbind()
            current.releaseAsr(); current.unbind()
        }
    }

    @Test fun realServiceRecognizesLanguagesReusesModelsAndRestartsAfterCancel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val verbose = SettingsPreferences.isVerboseLoggingEnabled(context)
        SettingsPreferences.setVerboseLoggingEnabled(context, true)
        com.kingzcheung.xime.util.FileLogger.setVerboseLoggingEnabled(true)
        val root = File(context.getExternalFilesDir(null), "speech-eval")
        val reports = JSONArray()
        val client = AsrInferenceClient(context)
        val updates = Collections.synchronizedList(mutableListOf<String>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val callback = object : AsrInferenceClient.AsrCallback {
            override fun onPartialResult(text: String) { updates.add(text) }
            override fun onFinalResult(text: String) { updates.add(text) }
            override fun onError(message: String) { errors.add(message) }
        }
        suspend fun runClip(mode: String, language: InputLanguage, clip: String) {
            assertTrue("Installed model missing: $mode", AsrModelManager(context).selection(mode, language).ready)
            val file = File(root, clip)
            assertTrue("Mandatory public WAV missing: $file", file.isFile)
            val pcm = readPcmWav(file.readBytes())
            updates.clear(); errors.clear()
            val startMs = measureTimeMillis { assertTrue(errors.toString(), client.startAsr(mode, callback, language)) }
            for (offset in pcm.indices step 3200) {
                client.pushAsrAudio(pcm.copyOfRange(offset, (offset + 3200).coerceAtMost(pcm.size)))
                delay(100)
            }
            lateinit var text: String
            val stopMs = measureTimeMillis { text = client.stopAsr() }
            reports.put(JSONObject().put("mode", mode).put("language", language.id).put("clip", clip)
                .put("start_ms", startMs).put("stop_ms", stopMs).put("text", text)
                .put("updates", JSONArray(updates.toList())))
            assertTrue(errors.toString(), errors.isEmpty())
            assertTrue("No recognition: $mode/$clip", text.isNotBlank())
            assertFalse(text.contains("<|"))
            when(language) {
                InputLanguage.CHINESE -> assertTrue(text, text.contains("时间") && text.contains("下午"))
                InputLanguage.ENGLISH -> assertTrue(text, text.contains("tribal", ignoreCase = true))
                InputLanguage.JAPANESE -> assertTrue(text, text.any { it in '\u3040'..'\u30ff' })
                else -> error("Unexpected test language")
            }
        }
        try {
            assertTrue(client.ensureBound())
            client.releaseAsr()
            client.setKeepModelAlive(true)
            runClip(SpeechModelCatalog.PARAFORMER, InputLanguage.CHINESE, "zh.wav")
            runClip(SpeechModelCatalog.TWO_PASS, InputLanguage.CHINESE, "zh.wav")
            runClip(SpeechModelCatalog.TWO_PASS, InputLanguage.CHINESE, "zh.wav")
            val cold = reports.getJSONObject(1).getLong("start_ms")
            val resident = reports.getJSONObject(2).getLong("start_ms")
            assertTrue("Resident restart took $resident ms vs cold $cold ms", resident < cold && resident < 1000)
            runClip(SpeechModelCatalog.TWO_PASS, InputLanguage.ENGLISH, "en.wav")
            runClip(SpeechModelCatalog.SENSEVOICE, InputLanguage.JAPANESE, "ja.wav")
            assertTrue(client.startAsr(SpeechModelCatalog.TWO_PASS, callback, InputLanguage.CHINESE))
            client.pushAsrAudio(readPcmWav(File(root, "zh.wav").readBytes()).take(16000).toByteArray())
            client.cancelAsr()
            val count = updates.size
            delay(600)
            assertEquals("Cancelled results escaped", count, updates.size)
            runClip(SpeechModelCatalog.TWO_PASS, InputLanguage.CHINESE, "zh.wav")
            assertTrue(client.startAsr(SpeechModelCatalog.TWO_PASS, callback, InputLanguage.CHINESE))
            repeat(10) { client.pushAsrAudio(ByteArray(3200)); delay(100) }
            assertEquals("Silence must not hallucinate text", "", client.stopAsr())
            client.unbind()
            // A subsequent start reconnects instead of silently dropping audio.
            assertTrue(client.startAsr(SpeechModelCatalog.TWO_PASS, callback, InputLanguage.CHINESE))
            client.cancelAsr()
        } finally {
            root.mkdirs()
            File(root, "service-results.json").writeText(reports.toString(2))
            client.setKeepModelAlive(SettingsPreferences.isSttKeepEngineAlive(context))
            client.releaseAsr(); client.unbind()
            SettingsPreferences.setVerboseLoggingEnabled(context, verbose)
            com.kingzcheung.xime.util.FileLogger.setVerboseLoggingEnabled(verbose)
        }
    }

    @Test fun disconnectedAudioIsAnErrorInsteadOfSilentDataLoss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = AsrInferenceClient(context)
        assertThrows(IllegalStateException::class.java) { client.pushAsrAudio(ByteArray(3200)) }
    }
}

internal fun readPcmWav(wav: ByteArray): ByteArray {
    fun int32(i: Int) = (0..3).fold(0) { v, n -> v or ((wav[i + n].toInt() and 255) shl (8 * n)) }
    require(String(wav, 0, 4) == "RIFF" && String(wav, 8, 4) == "WAVE")
    var offset = 12
    while (offset + 8 <= wav.size) {
        val size = int32(offset + 4)
        require(size >= 0 && offset.toLong() + 8 + size <= wav.size)
        if (String(wav, offset, 4) == "fmt ") {
            require(wav[offset + 8] == 1.toByte() && wav[offset + 10] == 1.toByte())
            require(int32(offset + 12) == 16000 && wav[offset + 22] == 16.toByte())
        }
        if (String(wav, offset, 4) == "data") return wav.copyOfRange(offset + 8, offset + 8 + size)
        offset += 8 + size + size % 2
    }
    error("No PCM data")
}
