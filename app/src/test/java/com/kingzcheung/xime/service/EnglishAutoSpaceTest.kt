package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class EnglishAutoSpaceTest {
    @Test fun completionGetsTheSpaceInTheSameReplacement() {
        val state = CandidateState(pendingEnglishText = "hel", associationCandidates = listOf("hello", "help"))
        var replaced: Pair<String, String>? = null
        assertTrue(commitAssociationSelection(state, 1, { error("must replace") }, { old, new -> replaced = old to new; true }, suffix = " "))
        assertEquals("hel" to "hello ", replaced)
        assertEquals("hello", associationWordAt(state, 1))
    }

    @Test fun originalWordOnlyAddsTheSpace() {
        val state = CandidateState(pendingEnglishText = "hel", associationCandidates = listOf("hello"))
        val committed = mutableListOf<String>()
        assertTrue(commitAssociationSelection(state, 0, { committed += it; true }, { _, _ -> error("already typed") }, suffix = " "))
        assertEquals(listOf(" "), committed)
        assertEquals("hel", associationWordAt(state, 0))
    }

    @Test fun offByDefaultLeavesSelectionUnchanged() {
        val state = CandidateState(pendingEnglishText = "hel", associationCandidates = listOf("hello"))
        assertTrue(commitAssociationSelection(state, 0, { error("nothing to add") }, { _, _ -> error("already typed") }))
        var replaced = ""
        assertTrue(commitAssociationSelection(state, 1, { error("must replace") }, { _, new -> replaced = new; true }))
        assertEquals("hello", replaced)
    }

    @Test fun nextKeyRules() {
        // 紧接着的空格不重复；句末标点挪到空格前面；右括号去掉空格；其他键照常。
        assertEquals("", EnglishAutoSpace.replacementFor("space"))
        for (p in listOf(".", ",", "?", "!", ":", ";")) assertEquals("$p ", EnglishAutoSpace.replacementFor(p))
        for (p in listOf(")", "]", "}")) assertEquals(p, EnglishAutoSpace.replacementFor(p))
        for (k in listOf("a", "A", "1", "(", "'", "\"", "-", "@", "，", "。", "enter", "delete", "ime_switch"))
            assertNull(k, EnglishAutoSpace.replacementFor(k))
    }

    @Test fun pageSwitchKeysKeepTheAutoSpaceForTheSymbolTypedNext() {
        for (k in listOf("shift", "number", "common_symbol", "abc", "mode_change")) assertTrue(k, EnglishAutoSpace.isNeutral(k))
        for (k in listOf("space", ",", "a", "ime_switch", "enter", "emoji")) assertFalse(k, EnglishAutoSpace.isNeutral(k))
    }

    @Test fun onlyTheNextKeyIsAffected() {
        val autoSpace = EnglishAutoSpace()
        assertFalse(autoSpace.isArmed)
        autoSpace.arm("hello")
        assertTrue(autoSpace.isArmed)
        assertEquals("hello ", autoSpace.take())
        assertNull(autoSpace.take())
        autoSpace.arm("hello")
        autoSpace.disarm()
        assertNull(autoSpace.take())
    }
}
