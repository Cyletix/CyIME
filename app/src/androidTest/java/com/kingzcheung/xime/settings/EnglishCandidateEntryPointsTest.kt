package com.kingzcheung.xime.settings

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EnglishCandidateEntryPointsTest {
    @Test fun upgradedInstalledLayoutsOfferWholeEnglishAndKeepFullChinese() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RimeEngine.getInstance()
        assertTrue(RimeConfigHelper.prepareEngine(context))
        assertTrue(engine.ensureSession())
        RimeEngine.rimeLock.lock()
        val previous = engine.getCurrentSchema()
        val ascii = engine.getOption("ascii_mode")
        val savedSchema = engine.getUserConfigString("var/previously_selected_schema").orEmpty()
        val savedAscii = engine.getUserConfigBool("var/option/ascii_mode")
        val report = mutableListOf<String>()
        try {
            val layouts = listOf("rime_ice", "pinyin_qwjrtk", "pinyin_cyletix10", "double_pinyin_flypy") +
                CustomKeyboardLayouts.load(context).map { it.id }
            for (schema in layouts.distinct()) {
                // Optional disabled resources are verified at the source boundary without enabling them.
                val source = File(SchemaManager.getRimeDir(context), "$schema.schema.yaml").readText()
                assertTrue("$schema must actually install the English policy", source.contains("- cyime_candidate_policy"))
                if (schema !in engine.getAvailableSchemas()) continue
                engine.clearComposition()
                assertTrue(engine.switchSchema(schema))
                engine.setOption("ascii_mode", false)
                for (word in listOf("fuck", "hello", "thanks", "world", "please", "computer")) {
                    val result = word.map { engine.processQueuedKeyAndGetResult(it.code, 0) }.last()
                    val candidates = engine.getAllCandidates(200).map { it.text }
                    val rank = candidates.indexOf(word)
                    report += "$schema $word rank=$rank input=${result.inputText} commit=${result.committedText} ascii=${result.isAsciiMode} batch=${result.candidates.take(3).map { it.text }}"
                    assertTrue("${report.last()}: complete English must be selectable", rank in 0..9)
                    if (schema == "rime_ice") assertEquals("${report.last()}: English beats abbreviation", 0, rank)
                    engine.clearComposition()
                }
            }
            assertTrue(engine.switchSchema("rime_ice"))
            for ((spelling, expected) in listOf("you" to "有", "shi" to "是", "nihao" to "你好", "fuchanke" to "妇产科")) {
                spelling.forEach { engine.processQueuedKeyAndGetResult(it.code, 0) }
                assertEquals("Full pinyin $spelling", expected, engine.getAllCandidates(1).first().text)
                engine.clearComposition()
            }
        } finally {
            File(context.cacheDir, "english-candidate-entrypoints.txt").writeText(report.joinToString("\n"))
            engine.clearComposition()
            engine.switchSchema(previous)
            engine.setOption("ascii_mode", ascii)
            engine.setUserConfigString("var/previously_selected_schema", savedSchema)
            engine.setUserConfigBool("var/option/ascii_mode", savedAscii)
            RimeEngine.rimeLock.unlock()
        }
    }
}
