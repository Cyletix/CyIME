package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Sentence-search pruning must not remove direct, complete dictionary words. */
class T9RecallRegressionTest {
    @Test fun familiarFullWordsRemainSelectableWithAndWithoutSeparators(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val schema = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        val phrases = listOf(
            "ni'hao" to "你好", "shu'ru'fa" to "输入法", "xie'xie" to "谢谢",
            "bei'jing" to "北京", "shang'hai" to "上海", "guang'zhou" to "广州",
            "yin'hang'ka" to "银行卡", "ming'tian" to "明天",
            "zhong'hua'ren'min'gong'he'guo" to "中华人民共和国",
        )
        fun digits(reading: String) = reading.map { letter ->
            if (letter == '\'') letter else ('2'.code +
                listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")
                    .indexOfFirst { letter in it }).toChar()
        }.joinToString("")
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            for ((reading, expected) in phrases) for (separated in listOf(false, true)) {
                engine.clearQueuedT9Composition()
                val input = digits(if (separated) reading else reading.replace("'", ""))
                input.forEach { engine.processQueuedT9KeyAndGetResult(it.code) }
                assertEquals(input, engine.getInput())
                val candidates = engine.getAllCandidates(100).map { it.text }
                assertTrue("$reading ($input): missing $expected in $candidates", expected in candidates)
            }
        } finally {
            engine.clearQueuedT9Composition()
            engine.switchSchema(schema)
            engine.setOption("ascii_mode", ascii)
        }
    }
}
