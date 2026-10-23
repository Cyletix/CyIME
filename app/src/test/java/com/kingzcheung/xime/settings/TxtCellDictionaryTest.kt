package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtCellDictionaryTest {
    private fun code(line: String) = TxtCellDictionary.parseLine(line)?.let { it.word + "|" + it.code }

    @Test
    fun `common line layouts`() {
        assertEquals("你好|ni hao", code("你好 ni hao"))
        assertEquals("你好|ni hao", code("你好\tni hao\t100"))      // Rime
        assertEquals("你好|ni hao", code("'ni'hao 你好"))            // Sogou export
        assertEquals("你好|ni hao", code("ni'hao 你好 1"))           // QQ export
        assertEquals("你好|ni hao", code("nihao,你好"))
        assertEquals("西安|xi an", code("西安 xian"))                // run together, split by character count
        assertEquals("策略|ce lve", code("策略 ce lue"))
    }

    @Test
    fun `lines without usable pinyin are skipped`() {
        assertNull(code("你好"))
        assertNull(code("你好 hello"))
        assertNull(code("你 ni"))  // single characters are not imported, as with SCEL
    }

    @Test
    fun `encodings and rime header`() {
        val text = "# comment\n---\nname: x\n...\n你好\tni hao\n世界\tshi jie\n"
        val parsed = TxtCellDictionary.parse(text.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("你好", "世界"), parsed.entries.map { it.word })
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "你好 nihao\r\n".toByteArray(Charsets.UTF_16LE)
        assertEquals("ni hao", TxtCellDictionary.parse(utf16).entries.single().code)
        val gbk = "你好 ni hao\n".toByteArray(charset("GB18030"))
        assertEquals("你好", TxtCellDictionary.parse(gbk).entries.single().word)
    }
}
