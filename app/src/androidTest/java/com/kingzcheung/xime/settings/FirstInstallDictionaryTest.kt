package com.kingzcheung.xime.settings

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FirstInstallDictionaryTest {
    @Test fun modelsPrepareWithoutOpeningMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deadline = android.os.SystemClock.elapsedRealtime() + 180_000L
        val manager = com.kingzcheung.xime.model.ModelManager
        // Catalog metadata is needed to inspect models already installed by an earlier run.
        runBlocking { manager.loadFromRemote(context) }
        while (android.os.SystemClock.elapsedRealtime() < deadline &&
            listOf("ochwpro", "zipformer-zh-int8").any { !manager.isModelDownloaded(context, it) }) {
            Thread.sleep(500)
        }
        for (id in listOf("ochwpro", "zipformer-zh-int8")) assertTrue("default model $id", manager.isModelDownloaded(context, id))
    }

    @Test fun bundledWordsWorkWithoutUserDictionaryOrPrediction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        val engine = RimeEngine.getInstance()
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context))
        assertTrue(engine.ensureSession())
        assertFalse(SettingsPreferences.shouldShowCandidateCancelButton(context))
        assertFalse(SettingsPreferences.isSmartPredictionEnabled(context))
        val samples = mapOf(
            "rime_ice" to listOf("zenme" to "怎么", "zenm" to "怎么", "dazi" to "打字"),
            "t9_pinyin" to listOf("93663" to "怎么", "3294" to "打字"),
            "pinyin_14jian" to listOf("zebme" to "怎么"),
            "double_pinyin_flypy" to listOf("zfme" to "怎么"))
        for ((schema, cases) in samples) {
            assertTrue(engine.switchSchema(schema))
            engine.setOption("ascii_mode", false)
            for ((code, word) in cases) {
                engine.clearQueuedComposition()
                engine.setInput(code)
                val candidates = engine.getCandidates().toList()
                assertTrue("$schema $code -> $candidates; missing $word", candidates.any { it == word })
            }
        }
        engine.clearQueuedComposition()
        val table = java.io.File(context.filesDir, "rime/build/rime_ice.table.bin")
        val backup = java.io.File(table.parentFile, "rime_ice.table.bin.test-backup")
        assertTrue(table.renameTo(backup))
        try { assertFalse("schema YAML alone must not mark deployment complete", RimeConfigHelper.isDeploymentComplete(context)) }
        finally { assertTrue(backup.renameTo(table)) }
    }
}
