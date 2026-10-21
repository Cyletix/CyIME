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

    private fun composing(preedit: String) = ClipboardSearch.composingInitials(preedit, initials::get)

    @Test fun aComposingWordGivesTheInitialOfEachSyllable() {
        assertEquals("dz", composing("di'zhi"))
        assertEquals("dz", composing("di zh"))
        assertEquals("dj", composing("dong jing"))
    }

    @Test fun abbreviationsGiveOneInitialPerLetterWithZhChShAsOne() {
        assertEquals("dz", composing("dz"))
        assertEquals("dz", composing("d'z"))
        assertEquals("zc", composing("zhch"))
        assertEquals("s", composing("sh"))
    }

    @Test fun hanAlreadyPickedInsideTheCompositionGivesItsInitial() {
        assertEquals("dz", composing("地zhi"))
        assertEquals("", composing("'' "))
    }

    @Test fun keypadDigitsFollowTheLettersOnEachKey() {
        assertEquals("39", ClipboardSearch.t9Digits("dz"))
        assertEquals("22233344455566677778889999", ClipboardSearch.t9Digits("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("39090", ClipboardSearch.t9Digits("DZ 090"))
    }

    @Test fun liveQueryIsBlankOnlyWithoutTextAndComposition() {
        assertTrue(ClipboardLiveQuery("", "").isBlank)
        assertFalse(ClipboardLiveQuery("", "d").isBlank)
        assertEquals("地di'zhi", ClipboardLiveQuery("地", "di'zhi").display)
    }

}
