package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardSearchTest {
    // A stand-in for ICU pinyin, enough for these characters.
    private val initials = mapOf('地' to 'd', '址' to 'z', '东' to 'd', '京' to 'j', '电' to 'd', '话' to 'h', '号' to 'h')
    private fun key(text: String) = ClipboardSearch.key(text, initials::get)

    @Test fun hanBecomesPinyinInitialsAndLatinStaysLowercase() {
        assertEquals("dz dj abc 123", key("地址：东京 ABC-123"))
        assertEquals("dhh 090", key("电话号 090"))
    }

    @Test fun textWithoutLettersStoresTheNonEmptyMarker() {
        assertEquals(ClipboardSearch.EMPTY_KEY, key("：，。！"))
        assertEquals(ClipboardSearch.EMPTY_KEY, key(""))
    }

    @Test fun onlyTheFirstCharactersAreIndexed() {
        val long = "a".repeat(ClipboardSearch.KEY_SOURCE_CHARS) + "地址"
        assertEquals(ClipboardSearch.KEY_SOURCE_CHARS, key(long).length)
    }

    @Test fun likePatternMatchesAnywhereAndTreatsWildcardsLiterally() {
        assertEquals("%dz%", ClipboardSearch.likePattern(" DZ "))
        assertEquals("%50\\%\\_off\\\\%", ClipboardSearch.likePattern("50%_off\\"))
    }

    @Test fun historyRejectsOnlyCopiesOverOneMegabyte() {
        assertTrue(ClipboardManager.fitsHistory("a".repeat(ClipboardManager.MAX_TEXT_BYTES)))
        assertFalse(ClipboardManager.fitsHistory("a".repeat(ClipboardManager.MAX_TEXT_BYTES + 1)))
        // Three bytes per character in UTF-8: a long Chinese copy is measured in bytes, not characters.
        assertFalse(ClipboardManager.fitsHistory("地".repeat(ClipboardManager.MAX_TEXT_BYTES / 3 + 1)))
        assertEquals(5000, ClipboardManager.MAX_ITEMS)
    }
}
