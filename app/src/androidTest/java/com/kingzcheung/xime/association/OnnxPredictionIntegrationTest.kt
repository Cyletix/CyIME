package com.kingzcheung.xime.association

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/** Mandatory public fixtures: adb push models to external-files/prediction-eval/<model-id>/. */
@RunWith(AndroidJUnit4::class)
class OnnxPredictionIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(context.getExternalFilesDir(null), "prediction-eval")

    @Test fun bothTransformerModelsProduceFiniteSuffixesAndRecordLatency() {
        assertTrue(NativeOnnxEngine.loadNativeLibrary(context))
        val reports = JSONArray()
        try {
            for (id in listOf("predictive-text-small", "predictive-text-base")) {
                val dir = File(root, id)
                val model = File(dir, "model_int8_dynamic.onnx")
                val vocabFile = File(dir, "vocab.json")
                assertTrue("Missing mandatory fixture: $model", model.isFile)
                assertTrue("Missing mandatory fixture: $vocabFile", vocabFile.isFile)
                NativeOnnxEngine.release()
                val loadMs = measureTimeMillis {
                    assertTrue("$id failed to load", NativeOnnxEngine.initialize(context, model.path))
                }
                assertTrue(NativeOnnxEngine.isInitialized())
                val json = JSONObject(vocabFile.readText())
                val vocab = when {
                    json.has("model") -> json.getJSONObject("model").getJSONObject("vocab")
                    json.has("vocab") -> json.getJSONObject("vocab")
                    else -> json
                }
                NativeOnnxEngine.initVocab(vocab.keys().asSequence().associateWith { vocab.getInt(it) })
                assertTrue(NativeOnnxEngine.predict("今天", 0).isEmpty())
                assertTrue(NativeOnnxEngine.predict("今天", -1).isEmpty())
                assertTrue(NativeOnnxEngine.predict(" ", 20).isEmpty())
                val cases = JSONArray()
                reports.put(JSONObject().put("model", id).put("load_ms", loadMs).put("cases", cases))
                for (text in listOf("今天", "今天天气", "那问题是", "我正在", "明天下午", "谢谢你的", "请帮我检查一下", "你好".repeat(100))) {
                    lateinit var results: List<AssociationCandidate>
                    val elapsed = measureTimeMillis { results = NativeOnnxEngine.predict(text, 20) }
                    assertTrue("$id: empty prediction for $text", results.isNotEmpty())
                    assertTrue(results.size <= 20)
                    assertTrue(results.all { it.text.isNotBlank() && it.score.isFinite() && it.text != "[UNK]" })
                    cases.put(JSONObject().put("context", text).put("ms", elapsed)
                        .put("candidates", JSONArray(results.map { it.text })))
                }
            }
        } finally {
            root.mkdirs()
            File(root, "native-results.json").writeText(reports.toString(2))
            NativeOnnxEngine.release()
        }
    }

    @Test fun missingModelDoesNotReportSuccessfulInitialization() {
        assertTrue(NativeOnnxEngine.loadNativeLibrary(context))
        NativeOnnxEngine.release()
        assertFalse("Native failure must not be reported as loaded",
            NativeOnnxEngine.initialize(context, File(root, "missing.onnx").path))
        assertFalse(NativeOnnxEngine.isInitialized())
    }
}
