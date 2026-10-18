package com.kingzcheung.xime.gloss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class CandidateGlossaryTest {

    /** 与生成脚本一致：键按 UTF-8 字节序排序，开头是 # 说明行。 */
    private fun table(vararg rows: Pair<String, String>): GlossTable {
        val body = rows.sortedWith { a, b ->
            val x = a.first.toByteArray(); val y = b.first.toByteArray()
            for (i in 0 until minOf(x.size, y.size)) {
                val d = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
                if (d != 0) return@sortedWith d
            }
            x.size - y.size
        }.joinToString("") { "${it.first}\t${it.second}\n" }
        return GlossTable.fromBuffer(ByteBuffer.wrap("# 来源与许可\n# 格式说明\n$body".toByteArray()))
    }

    @Test
    fun `finds both directions and skips header lines`() {
        val glossary = table("apple" to "苹果", "你好" to "hello", "电脑" to "computer", "app" to "应用")
        assertEquals(4, glossary.size)
        assertEquals("苹果", glossary.lookup("apple"))
        assertEquals("hello", glossary.lookup("你好"))
        assertEquals("computer", glossary.lookup("电脑"))
    }

    @Test
    fun `prefix keys do not match each other`() {
        val glossary = table("app" to "应用", "apple" to "苹果", "apples" to "苹果们")
        assertEquals("应用", glossary.lookup("app"))
        assertEquals("苹果", glossary.lookup("apple"))
        assertNull(glossary.lookup("appl"))
        assertNull(glossary.lookup("applesauce"))
        assertNull(glossary.lookup(""))
        assertNull(glossary.lookup("# 来源与许可"))
    }

    @Test
    fun `ascii candidates are looked up case-insensitively, chinese as written`() {
        assertEquals("hello", CandidateGlossary.keyOf("Hello"))
        assertEquals("ios", CandidateGlossary.keyOf(" iOS "))
        assertEquals("你好", CandidateGlossary.keyOf("你好"))
    }

    @Test
    fun `annotate keeps original comments while no glossary is loaded`() {
        assertEquals(listOf("ni hao", "ni hao"),
            CandidateGlossary.annotate(listOf("你好", "拟好"), listOf("ni hao", "ni hao")))
        assertEquals("ni hao", CandidateGlossary.annotate("你好", "ni hao"))
    }
}
