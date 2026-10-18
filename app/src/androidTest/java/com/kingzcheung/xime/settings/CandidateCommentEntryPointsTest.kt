package com.kingzcheung.xime.settings

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the real batched JNI entry used by typing, not only the legacy host probe. */
class CandidateCommentEntryPointsTest {
    @Test fun legacyAndBatchedTypingReturnPinyinAfterEveryLayoutSwitch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RimeEngine.getInstance()
        assertTrue(RimeConfigHelper.prepareEngine(context))
        assertTrue(engine.ensureSession())
        val oldSchema = engine.getCurrentSchema()
        val oldAscii = engine.getOption("ascii_mode")
        val oldComments = engine.getOption("_cyime_candidate_comments")
        val savedSchema = engine.getUserConfigString("var/previously_selected_schema").orEmpty()
        val savedAscii = engine.getUserConfigBool("var/option/ascii_mode")
        try {
            for (batched in listOf(false, true)) {
                for ((schema, input) in listOf("rime_ice" to "nihao", "pinyin_14jian" to "bugao", "t9_pinyin" to "64426")) {
                    engine.clearComposition()
                    assertTrue(engine.switchSchema(schema))
                    engine.setOption("ascii_mode", false)
                    if (schema == "t9_pinyin") engine.clearQueuedT9Composition()
                    // Force a fresh option state so the legacy path cannot mask a broken batch path.
                    engine.setOption("_cyime_candidate_comments", false)
                    if (batched) {
                        val result = input.map {
                            if (schema == "t9_pinyin") requireNotNull(engine.processQueuedT9KeyAndGetResult(it.code)).state
                            else engine.processQueuedKeyAndGetResult(it.code, 0)
                        }.last()
                        assertTrue("batch $schema has real annotations: ${result.candidates.toList()}",
                            result.candidates.any { candidate -> candidate.comment.any { it in 'a'..'z' } })
                    } else {
                        input.forEach {
                            if (schema == "t9_pinyin") engine.processQueuedT9Key(it.code)
                            else engine.processKey(it.code, 0)
                        }
                        val rows = engine.getAllCandidates(10)
                        assertTrue("legacy $schema has real annotations: ${rows.toList()}",
                            rows.any { candidate -> candidate.comment.any { it in 'a'..'z' } })
                    }
                }
            }
        } finally {
            engine.clearComposition()
            engine.switchSchema(oldSchema)
            engine.setOption("ascii_mode", oldAscii)
            engine.setOption("_cyime_candidate_comments", oldComments)
            engine.setUserConfigString("var/previously_selected_schema", savedSchema)
            engine.setUserConfigBool("var/option/ascii_mode", savedAscii)
        }
    }
}
