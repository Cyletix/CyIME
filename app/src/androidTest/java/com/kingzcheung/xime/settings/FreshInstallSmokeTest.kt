package com.kingzcheung.xime.settings

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import org.junit.Assert.*
import org.junit.Test

/** Run in the isolated freshcheck application; never clears an installed user's data. */
class FreshInstallSmokeTest {
    @Test fun bundledDefaultsAutomaticallyDeployAndProduceChineseWithoutDownloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Use the isolated freshcheck APK", context.packageName.endsWith(".freshcheck"))
        val (user, shared) = RimeConfigHelper.initializeRimeData(context)
        assertEquals(listOf("rime_ice", "t9_pinyin"), SchemaManager.getEnabledSchemas(context))
        val engine = RimeEngine.getInstance()
        engine.initialize(user, shared)
        assertTrue("Offline deployment", RimeConfigHelper.ensureDeployment(context))
        assertTrue(engine.ensureSession(180_000L))
        assertTrue(engine.switchSchema("rime_ice"))
        engine.setOption("ascii_mode", false)
        engine.setInput("zenme")
        assertTrue("26-key 怎么", engine.getCandidates().contains("怎么"))
        engine.clearComposition()
        assertTrue(engine.switchSchema("t9_pinyin"))
        engine.setOption("ascii_mode", false)
        engine.clearQueuedT9Composition()
        engine.processQueuedT9Key('3'.code)
        assertTrue("T9 的", engine.getCandidates().contains("的"))
        engine.clearQueuedT9Composition()
        engine.clearComposition()
        assertTrue(engine.switchSchema("rime_ice"))
        assertEquals(listOf("rime_ice", "t9_pinyin", InputModes.ENGLISH),
            InputModes.ordered(context, SchemaManager.discoverSchemas(context)
                .filter { it.schemaId in SchemaManager.getEnabledSchemas(context) && SchemaManager.isSchemaCompiled(context, it.schemaId) }
                .map { SchemaInfo(it.schemaId, it.name, it.version, it.author, it.description) }).map { it.schemaId })
    }
}
