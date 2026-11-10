package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishNextWordContextTest {
    @Test fun readsTheLastWordAndPairAfterSpace() {
        val context = EnglishNextWordContext.parse("I thank ")
        assertEquals("thank", context.previous)
        assertEquals("I" to "thank", context.pair)
        assertEquals("thank", context.lastToken)
        assertTrue(context.justCompleted)
    }

    @Test fun stillTypingIsNotAWordEnd() {
        val context = EnglishNextWordContext.parse("I thank")
        assertFalse(context.justCompleted)
        assertNull(context.pair)
        assertEquals("thank", context.previous)
    }

    @Test fun doubleSpaceEndsNothing() {
        val context = EnglishNextWordContext.parse("I thank  ")
        assertFalse(context.justCompleted)
        assertNull(context.pair)
        assertEquals("thank", context.previous)
    }

    @Test fun addressIsAPhraseNotAWordToPredictFrom() {
        val context = EnglishNextWordContext.parse("my zhang.san@example.com ")
        assertNull(context.previous)
        assertNull(context.pair)
        assertEquals("zhang.san@example.com", context.lastToken)
        assertTrue(context.justCompleted)
    }

    @Test fun apostropheStaysInTheWordButNotInTheLookupKey() {
        val context = EnglishNextWordContext.parse("don't ")
        assertEquals("don't", context.previous)
        assertEquals("dont", EnglishNextWordContext.normalize(context.previous))
    }

    @Test fun punctuationBeforeTheSpaceIsNotAWordEnd() {
        val context = EnglishNextWordContext.parse("thank, ")
        assertFalse(context.justCompleted)
        assertNull(context.pair)
    }

    @Test fun emptyAndNullHaveNoContext() {
        assertEquals(EnglishNextWordContext.EMPTY, EnglishNextWordContext.parse(null))
        assertEquals(EnglishNextWordContext.EMPTY, EnglishNextWordContext.parse(""))
    }
}
