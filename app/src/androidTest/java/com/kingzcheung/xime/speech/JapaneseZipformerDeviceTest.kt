package com.kingzcheung.xime.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.model.*
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.MarketVersionStore
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections

/** Opt-in local fixture test. Installs only the explicitly staged, checksum-verified Japanese model. */
@RunWith(AndroidJUnit4::class)
class JapaneseZipformerDeviceTest {
    @Test fun installAndRecognizeJapanese() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "speech-eval/zipformer-ja")
        assumeTrue("Stage model and PCM16 fixtures before running", File(root, "tokens.txt").isFile)
        val modelId = InstrumentationRegistry.getArguments().getString("model") ?: SpeechModelCatalog.ZIPFORMER_JA
        require(SpeechModelCatalog.isJapaneseZipformer(modelId))
        val model = SpeechModelCatalog.models.first { it.id == modelId }
        val target = ModelStorage.getModelDir(context, model.id)
        val version = model.resolvedVersion()!!
        validateDownloadedModel(root, version)
        if (!hasDownloadedModelFiles(target, version)) {
            val staging = File(ModelStorage.getModelsRoot(context), ".ja-device-test").apply { mkdirs() }
            version.files.forEach { File(root, it.name).copyTo(File(staging, it.name), overwrite = true) }
            validateDownloadedModel(staging, version)
            installDownloadedModel(staging, target)
        }
        MarketVersionStore.setModelVersion(context, model.id, version.version)
        ModelManager.notifyInstalledModelsChanged()
        val manager = AsrModelManager(context)
        // Explicit opt-in setup lets this requested model remain selected for subsequent microphone testing.
        if (InstrumentationRegistry.getArguments().getString("selectJapanese") == "true") {
            manager.setModel(model.id, InputLanguage.JAPANESE)
            SettingsPreferences.setSttUseLocal(context, true)
            SettingsPreferences.setSttEnabled(context, true)
        }
        val selection = manager.selection(model.id, InputLanguage.JAPANESE)
        assertTrue(selection.ready)
        val started = System.nanoTime()
        val engine = SherpaSpeechEngine(context, selection)
        val loadMs = (System.nanoTime() - started) / 1e6
        val results = JSONArray()
        try {
            assertFalse(engine.isStreaming)
            // Only the fixed corpus: a microphone diagnostic may also live in this directory.
            for (name in listOf("part-1-seg-02.pcm", "part-2-seg-02.pcm")) {
                val file = File(root, name)
                val pcm = file.readBytes()
                val samples = FloatArray(pcm.size / 2) { i ->
                    (((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 255)).toShort()).toFloat() / 32768f
                }
                val decodeAt = System.nanoTime()
                val whole = engine.refine(samples)
                val decodeMs = (System.nanoTime() - decodeAt) / 1e6
                val errors = Collections.synchronizedList(mutableListOf<String>())
                val regions = JSONArray()
                val updates = Collections.synchronizedList(mutableListOf<Pair<Double, String>>())
                val sessionAt = System.nanoTime()
                val session = LocalSpeechSession(engine, { updates.add((System.nanoTime() - sessionAt) / 1e6 to it) }, { errors.add(it) }, { id, count, reason ->
                    regions.put(JSONObject().put("id", id).put("samples", count).put("reason", reason.name))
                })
                try {
                    for (offset in pcm.indices step 3200) {
                        session.acceptPcm(pcm.copyOfRange(offset, minOf(offset + 3200, pcm.size)))
                        Thread.sleep(100)
                    }
                    val stoppedAt = System.nanoTime()
                    val text = session.finish()
                    val stopMs = (System.nanoTime() - stoppedAt) / 1e6
                    session.awaitIdle()
                    results.put(JSONObject().put("clip", file.name).put("load_ms", loadMs).put("whole_ms", decodeMs)
                        .put("whole", whole).put("session", text).put("audio_ms", pcm.size / 32.0)
                        .put("stop_ms", stopMs).put("regions", regions).put("errors", JSONArray(errors.toList())))
                    results.getJSONObject(results.length() - 1).put("updates", JSONArray(updates.map { (time, value) ->
                        JSONObject().put("ms", time).put("text", value)
                    }))
                    File(root, "results.json").writeText(results.toString(2))
                    assertTrue("$errors", errors.isEmpty())
                    assertTrue(text.isNotBlank())
                    assertTrue(text.any { it in '\u3040'..'\u30ff' })
                    assertTrue("No text while speaking: $updates", updates.any { it.first < 4500 && it.second.isNotBlank() })
                    assertTrue("No ongoing updates: $updates", updates.size >= 3)
                } finally { session.cancel(); session.awaitIdle() }
            }
            assertTrue("No PCM fixtures evaluated", results.length() > 0)
        } finally { engine.close() }
    }
}
