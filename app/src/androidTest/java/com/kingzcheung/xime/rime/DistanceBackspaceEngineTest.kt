package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.DragCode
import com.kingzcheung.xime.service.InputUIState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DistanceBackspaceEngineTest {
    @Test fun restoringEmptyCompositionKeepsRawKeysCandidatesAndOrdinaryBackspace(): Unit = runBlocking {
        assertTrue(RimeConfigHelper.prepareEngine(InstrumentationRegistry.getInstrumentation().targetContext))
        val engine = RimeEngine.getInstance()
        val previous = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        try {
            for ((schema, raw) in listOf("rime_ice" to "nihao", "pinyin_14jian" to "BUGAO", "t9_pinyin" to "64426")) {
                assertTrue(engine.switchSchema(schema))
                engine.setOption("ascii_mode", false)
                val nine = schema == "t9_pinyin"
                fun press(key: Int) {
                    if (nine) engine.processQueuedT9KeyAndGetResult(key) else engine.processQueuedKeyAndGetResult(key, 0)
                }
                raw.forEach { press(it.code) }
                val candidates = engine.getAllCandidates(5).map { it.text }
                assertTrue(candidates.isNotEmpty())
                val gesture = checkNotNull(DragCode.open(engine, InputUIState(currentSchemaId = schema)))
                for (count in listOf(1, 3, 1, 99, 0)) {
                    val expected = raw.dropLast(count.coerceAtMost(raw.length))
                    assertEquals(expected, gesture.move(engine, count) { true }?.inputText)
                    if (nine) assertEquals(expected, engine.t9GetRemainingDigits())
                }
                assertEquals(candidates, engine.getAllCandidates(5).map { it.text })
                press(0xff08)
                assertEquals(raw.dropLast(1), engine.getInput())
                if (nine) engine.clearQueuedT9Composition() else engine.clearComposition()
            }
        } finally {
            engine.clearQueuedT9Composition()
            engine.switchSchema(previous)
            engine.setOption("ascii_mode", ascii)
        }
    }
}
