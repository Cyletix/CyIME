package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSuffixesTest {
    @Test
    fun atAfterMailboxNameOffersAllSuffixes() {
        assertEquals(EmailSuffixes.DOMAINS, EmailSuffixes.suggestions("abc123@"))
        assertEquals(EmailSuffixes.DOMAINS, EmailSuffixes.suggestions("我的邮箱是 zhang.san_01@"))
    }

    @Test
    fun atWithoutMailboxNameIsAMentionNotAnEmail() {
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("@"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("hi @"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("微博@"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("abc"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions(null))
    }

    @Test
    fun typedPartKeepsOnlyMatchingSuffixes() {
        assertEquals(listOf("gmail.com"), EmailSuffixes.suggestions("abc@g"))
        assertEquals(listOf("163.com", "126.com"), EmailSuffixes.suggestions("abc@1"))
        assertEquals(listOf("hotmail.com"), EmailSuffixes.suggestions("abc@HO"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("abc@company"))
        // 打完了就不再给。
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("abc@gmail.com"))
        assertEquals(listOf("gmail.com"), EmailSuffixes.matching("gm"))
    }

    @Test
    fun remainderIsOnlyTheUntypedPart() {
        assertEquals("qq.com", EmailSuffixes.remainder("abc@", "qq.com"))
        assertEquals("mail.com", EmailSuffixes.remainder("abc@g", "gmail.com"))
        assertEquals("3.com", EmailSuffixes.remainder("abc@16", "163.com"))
        // 光标前已经对不上（挪了光标、删了 @）：不补。
        assertNull(EmailSuffixes.remainder("abc@q", "gmail.com"))
        assertNull(EmailSuffixes.remainder("abc", "qq.com"))
    }

    @Test
    fun fullWidthAtFromChineseSymbolPanelAlsoCounts() {
        assertEquals(EmailSuffixes.DOMAINS, EmailSuffixes.suggestions("abc＠"))
        assertEquals(listOf("qq.com"), EmailSuffixes.suggestions("abc＠q"))
        assertEquals(emptyList<String>(), EmailSuffixes.suggestions("微博＠"))
    }

    @Test
    fun completionReplacesTypedPartAndMakesAtHalfWidth() {
        assertEquals("@" to "@qq.com", EmailSuffixes.completion("abc@", "qq.com"))
        assertEquals("@q" to "@qq.com", EmailSuffixes.completion("abc@q", "qq.com"))
        assertEquals("@qq.c" to "@qq.com", EmailSuffixes.completion("abc@qq.c", "qq.com"))
        assertEquals("＠16" to "@163.com", EmailSuffixes.completion("abc＠16", "163.com"))
        assertNull(EmailSuffixes.completion("abc@q", "gmail.com"))
        assertNull(EmailSuffixes.completion("abc", "qq.com"))
        assertNull(EmailSuffixes.completion(null, "qq.com"))
    }

    @Test
    fun nextKeyContinuesOnlyWhileSomeSuffixStillMatches() {
        assertTrue(EmailSuffixes.continues("abc@", "q"))
        assertTrue(EmailSuffixes.continues("abc@", "1"))
        assertTrue(EmailSuffixes.continues("abc@qq", "."))
        assertTrue(EmailSuffixes.continues("abc＠g", "m"))
        // 对不上任何后缀：这个键照常进拼音。
        assertFalse(EmailSuffixes.continues("abc@", "z"))
        assertFalse(EmailSuffixes.continues("abc@q", "x"))
        assertFalse(EmailSuffixes.continues("abc@qq.com", "m"))
        assertFalse(EmailSuffixes.continues("你好", "q"))
    }

    @Test
    fun onByDefault() {
        assertTrue(com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences().emailSuffixes)
    }

    @Test
    fun onlyAListOfSuffixesCountsAsSuggestions() {
        assertTrue(EmailSuffixes.isSuggestionList(listOf("gmail.com")))
        assertFalse(EmailSuffixes.isSuggestionList(emptyList()))
        assertFalse(EmailSuffixes.isSuggestionList(listOf("gmail.com", "good")))
        assertFalse(EmailSuffixes.isSuggestionList(listOf("我们")))
    }
}
