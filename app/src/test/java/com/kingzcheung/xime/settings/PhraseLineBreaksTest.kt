package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhraseLineBreaksTest {
    private val invoice = "名称：某某公司\n税号：91110000XXXX"

    @Test
    fun `typed and pasted line breaks are stored on one table line and come back as line breaks`() {
        for (text in listOf(invoice, "名称：某某公司\r\n税号：91110000XXXX", "名称：某某公司\r税号：91110000XXXX")) {
            val stored = PhraseLineBreaks.stored(text)
            assertFalse(stored.contains('\n'))
            assertFalse(stored.contains('\r'))
            assertEquals(invoice, PhraseLineBreaks.typed(stored))
        }
    }

    @Test
    fun `a tab cannot push the code out of its column`() {
        assertEquals("名称 某某公司", PhraseLineBreaks.stored("名称\t某某公司"))
    }

    @Test
    fun `candidates show the break as a mark and stay on one line`() {
        assertEquals("名称：某某公司↵税号：91110000XXXX", PhraseLineBreaks.shown(PhraseLineBreaks.stored(invoice)))
    }

    @Test
    fun `one line phrases are untouched`() {
        for (text in listOf("B站", "C:\\new folder", "a\\nb", "↵")) {
            assertEquals(text, PhraseLineBreaks.stored(text))
            assertEquals(text, PhraseLineBreaks.typed(text))
            assertEquals(text, PhraseLineBreaks.shown(text))
        }
    }
}
