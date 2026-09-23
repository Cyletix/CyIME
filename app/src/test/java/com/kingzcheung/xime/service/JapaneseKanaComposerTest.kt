package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class JapaneseKanaComposerTest {
    @Test fun `voicing cycle replaces only the last uncommitted kana`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("koni", "ha", "koniha", false)
        for (next in listOf("ba", "pa", "ha")) {
            val plan = composer.replacement(if (next == "ba") "koniha" else if (next == "pa") "koniba" else "konipa")!!
            assertEquals("koni$next", plan.input)
            composer.recordReplacement(plan, plan.input)
        }
    }

    @Test fun `small kana replacement preserves the preceding composition`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("ki", "ya", "kiya", false)
        val plan = composer.replacement("kiya")!!
        assertEquals("kixya", plan.input)
        composer.recordReplacement(plan, "kixya")
        assertEquals("kiya", composer.replacement("kixya")!!.input)
    }

    @Test fun `tsu cycles through voiced and small forms`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("", "tu", "tu", false)
        val voiced = composer.replacement("tu")!!
        assertEquals("du", voiced.input)
        composer.recordReplacement(voiced, "du")
        assertEquals("xtu", composer.replacement("du")!!.input)
    }

    @Test fun `modifier after commit cannot create a replacement`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("", "ka", "ka", false)
        assertNull(composer.replacement(""))
        // 即使后来由其他输入路径生成同样的编码，也不能复活旧替换状态。
        assertNull(composer.replacement("ka"))
    }

    @Test fun `engine commit during input invalidates the tail`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("ni", "ka", "nika", true)
        assertNull(composer.replacement("nika"))
    }

    @Test fun `backspace or external composition changes invalidate the tail`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("ni", "ha", "niha", false)
        assertNull(composer.replacement("nih"))
        assertNull(composer.replacement("niha"))
    }

    @Test fun `failed or partial input cannot become a modifier target`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("", "ka", "k", false)
        assertNull(composer.replacement("k"))
    }

    @Test fun `failed replacement invalidates the remembered tail`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("", "ha", "ha", false)
        val plan = composer.replacement("ha")!!
        composer.recordReplacement(plan, "ha")
        assertNull(composer.replacement("ha"))
    }

    @Test fun `new input session cannot reuse a previous modifier target`() {
        val composer = JapaneseKanaComposer()
        composer.recordInput("", "ha", "ha", false)
        composer.reset()
        assertNull(composer.replacement("ha"))
    }
}
