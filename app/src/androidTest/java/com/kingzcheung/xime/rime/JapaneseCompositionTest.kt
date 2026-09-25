package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.*
import com.kingzcheung.xime.settings.JapaneseSchemas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JapaneseCompositionTest {
    @Test fun wholeKanaDeletionConversionRangeAndKatakanaUseTheInstalledEngine() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        val engine = RimeEngine.getInstance()
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context)); assertTrue(engine.ensureSession())
        val previous = engine.getCurrentSchema()
        try {
            JapaneseSchemas.ids.forEach { schema ->
                assertTrue(engine.switchSchema(schema)); engine.setOption("ascii_mode", false)
                for ((input, expected) in mapOf("na" to "", "ka" to "", "nn" to "", "xtu" to "", "kakina" to "kaki", "KATAKANA" to "KATAKA", "kitta" to "kit")) {
                    engine.setInput(input)
                    val end = engine.previousJapaneseBoundary(input)
                    assertEquals("$schema 删除整个假名: $input", expected, input.take(end))
                    assertEquals("边界查询不得更改编码", input, engine.getInput())
                }
                engine.setInput("KATAKA")
                assertEquals("片假名读音保持", "カタカ", engine.processJapaneseEnterIfComposing()!!.committedText)
                engine.setInput("naniwoshimasuka")
                val conversion = JapaneseConversion(engine, engine.getInput())
                conversion.refresh()
                assertTrue("整句: ${conversion.preview}", conversion.preview.contains("何"))
                conversion.moveRange(-4)
                assertTrue("只转换前3假名: ${conversion.preview}", conversion.preview.startsWith("何") && conversion.preview.endsWith("しますか"))
                val first = conversion.candidateIndex
                conversion.cycle()
                assertNotEquals(first, conversion.candidateIndex)
                assertEquals("预览不得提交", "", engine.commit())
                assertEquals("预览保留全部原编码", "naniwoshimasuka", engine.getInput())
                assertEquals("可撤回原假名", "なにをしますか", engine.japaneseReading(engine.getInput()))
            }
        } finally { engine.clearComposition(); if (previous.isNotBlank()) engine.switchSchema(previous) }
    }

    /**
     * 26 键罗马音只按下声母时，屏幕必须显示按下的字母，而不是原方案为促音/拨音
     * 准备的回显（k→っ、n→ん）；已拼完的假名仍沿用原方案回显。
     */
    @Test fun partialRomajiShowsPressedLettersWhileCompleteKanaKeepTheirEcho() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        val engine = RimeEngine.getInstance()
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context)); assertTrue(engine.ensureSession())
        val previous = engine.getCurrentSchema()
        try {
            JapaneseSchemas.ids.forEach { schema ->
                assertTrue(engine.switchSchema(schema)); engine.setOption("ascii_mode", false)
                assertEquals("$schema 单个声母显示按下的字母", "k", displayText(engine, "k"))
                assertEquals("$schema 单个 n 显示按下的字母", "n", displayText(engine, "n"))
                assertEquals("$schema nn 是完整假名，沿用回显", "ん", displayText(engine, "nn"))
                assertEquals("$schema ka 是完整假名，沿用回显", "か", displayText(engine, "ka"))
                assertEquals("$schema 已构成部分保留假名，尾部显示字母", "かk", displayText(engine, "kak"))
                assertEquals("$schema 完整假名不改写", "かん", displayText(engine, "kann"))
                assertEquals("$schema 以元音结尾不改写", "かきな", displayText(engine, "kakina"))
                engine.setInput("kak")
                engine.japaneseDisplayText(engine.getInput())
                assertEquals("显示探测不得更改编码", "kak", engine.getInput())
            }
        } finally { engine.clearComposition(); if (previous.isNotBlank()) engine.switchSchema(previous) }
    }

    /** 显示文本：未被改写时即原方案回显，二者用同一断言口径。 */
    private fun displayText(engine: RimeEngine, input: String): String {
        engine.setInput(input)
        return engine.japaneseDisplayText(input) ?: engine.japaneseReading(input)
    }
}
