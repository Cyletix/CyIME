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
    fun onlyAListOfSuffixesCountsAsSuggestions() {
        assertTrue(EmailSuffixes.isSuggestionList(listOf("gmail.com")))
        assertFalse(EmailSuffixes.isSuggestionList(emptyList()))
        assertFalse(EmailSuffixes.isSuggestionList(listOf("gmail.com", "good")))
        assertFalse(EmailSuffixes.isSuggestionList(listOf("我们")))
    }
}
