package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.DictEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DigitPhrasesTest {
    private val phrases = listOf(
        DictEntry("me@example.com", "1"),
        DictEntry("https://example.com", "12"),
        DictEntry("13800138000", "138"),
        DictEntry("我的地址", "1", 5),
        DictEntry("hello", "hi"),
    )

    @Test
    fun trailingDigitsOnly() {
        assertEquals("123", DigitPhrases.typedDigits("abc123"))
        assertEquals("１２", DigitPhrases.typedDigits("电话１２"))
        assertNull(DigitPhrases.typedDigits("123a"))
        assertNull(DigitPhrases.typedDigits(""))
        assertNull(DigitPhrases.typedDigits(null))
        assertEquals("12", DigitPhrases.code("１２"))
    }

    @Test
    fun exactCodeFirstThenLongerCodes() {
        // 编码正好是 1 的两条在前（权重高的先），再是编码以 1 开头的。
        assertEquals(listOf("我的地址", "me@example.com", "https://example.com", "13800138000"),
            DigitPhrases.matching("1", phrases))
        assertEquals(listOf("https://example.com"), DigitPhrases.matching("12", phrases))
        assertEquals(listOf("13800138000"), DigitPhrases.suggestions("号码138", phrases))
        assertEquals(emptyList<String>(), DigitPhrases.matching("9", phrases))
        assertEquals(emptyList<String>(), DigitPhrases.matching("", phrases))
        assertEquals(emptyList<String>(), DigitPhrases.suggestions("abc", phrases))
    }

    @Test
    fun phraseSameAsDigitsIsSkipped() {
        assertEquals(emptyList<String>(), DigitPhrases.matching("110", listOf(DictEntry("110", "110"))))
    }

    @Test
    fun readsOnlyDigitCodesAndRereadsWhenFileChanges() {
        val file = File.createTempFile("custom_phrase", ".txt")
        try {
            file.writeText("# Rime table\n#@/db_name\tcustom_phrase\nme@example.com\t1\n你好\tnihao\n")
            assertEquals(listOf(DictEntry("me@example.com", "1")), DigitPhrases.phrases(file))
            file.writeText("# Rime table\nme@example.com\t1\nhttps://example.com\t12\n")
            file.setLastModified(file.lastModified() + 2000)
            assertEquals(2, DigitPhrases.phrases(file).size)
            assertEquals(2, DigitPhrases.cachedPhrases().size)
        } finally {
            file.delete()
        }
    }
}
