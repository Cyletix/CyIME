package com.kingzcheung.xime.association

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.system.measureTimeMillis

class PredictionLifecycleTest {
    @Test fun hotModelSwitchUsesChosenWeightsAndReleaseNeverWaitsOnUi() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = SettingsPreferences.getPredictionSelectedModel(context)
        val enabled = SettingsPreferences.isSmartPredictionEnabled(context)
        val report = JSONArray()
        try {
            for (id in listOf("predictive-text-small", "predictive-text-base", "predictive-text-small")) {
                SettingsPreferences.setPredictionSelectedModel(context, id)
                AssociationManager.release()
                val loadMs = measureTimeMillis { assertTrue(AssociationManager.initialize(context)) }
                assertEquals("A HOT previous model must not survive a selection change", id, OnnxAssociationEngine.activeModelId())
                lateinit var candidates: List<AssociationCandidate>
                val predictMs = measureTimeMillis { candidates = AssociationManager.predict("今天天气", 10) }
                assertTrue(candidates.isNotEmpty())
                report.put(JSONObject().put("model", id).put("load_ms", loadMs).put("predict_ms", predictMs)
                    .put("candidates", JSONArray(candidates.map { it.text })))
            }
            val pending = async(Dispatchers.Default) { OnnxAssociationEngine.predict("你好".repeat(32)) }
            var releaseMs = 0L
            instrumentation.runOnMainSync { releaseMs = measureTimeMillis { AssociationManager.release() } }
            assertTrue("Release blocked UI for $releaseMs ms", releaseMs < 100)
            assertFalse(OnnxAssociationEngine.isInitialized())
            pending.await()
            report.put(JSONObject().put("ui_release_ms", releaseMs))
            assertTrue(AssociationManager.initialize(context))
            assertEquals("predictive-text-small", OnnxAssociationEngine.activeModelId())
            assertTrue(OnnxAssociationEngine.predict("谢谢你的").isNotEmpty())
            AssociationManager.release()
            val loading = async(Dispatchers.IO) { AssociationManager.initialize(context) }
            delay(10)
            instrumentation.runOnMainSync { AssociationManager.release() }
            loading.await()
            assertFalse("An in-flight load must not turn prediction back on", OnnxAssociationEngine.isInitialized())
        } finally {
            File(context.getExternalFilesDir(null), "prediction-eval/lifecycle-results.json").writeText(report.toString(2))
            AssociationManager.release()
            SettingsPreferences.setPredictionSelectedModel(context, original)
            if (enabled) AssociationManager.initialize(context)
        }
    }
}
