package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class JapaneseTypingTest {
    @Test fun uppercaseKanaEncodingIsPreserved() {
        assertEquals("KATAKANA", "katakana".map { JapaneseTyping.keyCode(it.toString(), true).toChar() }.joinToString(""))
        assertEquals('k'.code, JapaneseTyping.keyCode("K", false))
    }
    @Test fun japaneseCaseDoesNotAffectChineseOrEnglish() {
        assertTrue(JapaneseTyping.usesKanaCase("japanese", false))
        assertTrue(JapaneseTyping.usesKanaCase("jaroomaji", false))
        assertFalse(JapaneseTyping.usesKanaCase("japanese", true))
        assertFalse(JapaneseTyping.usesKanaCase("pinyin_simp", false))
        assertFalse(JapaneseTyping.usesKanaCase("japanese_kana", false))
    }
}
