package com.kingzcheung.xime.handwriting

import org.junit.Assert.*
import org.junit.Test

class HandwritingCandidatePolicyTest {
    @Test fun symbolsCannotDisplaceHanOrInflateItsConfidence() {
        val candidates = listOf(HandwritingCandidate("@", .8f), HandwritingCandidate("|", .7f),
            HandwritingCandidate("中", .15f), HandwritingCandidate("文", .03f), HandwritingCandidate("中", .01f))
        assertEquals(listOf(HandwritingCandidate("中", .15f), HandwritingCandidate("文", .03f)),
            HandwritingCandidatePolicy.select(candidates, 5))
    }
    @Test fun rareAndTraditionalHanRemainAvailable() {
        listOf("一", "漢", "〇", "㐀", "𠀀").forEach { assertTrue(it, HandwritingCandidatePolicy.isHanCharacter(it)) }
        listOf("", "，", "A", "1", "⺀", "|", "你好", "👋").forEach { assertFalse(it, HandwritingCandidatePolicy.isHanCharacter(it)) }
    }
    @Test fun emptyOrInvalidCandidatesDoNotRefillWithSymbols() {
        assertTrue(HandwritingCandidatePolicy.select(listOf(HandwritingCandidate("!", .9f), HandwritingCandidate("中", Float.NaN)), 5).isEmpty())
        assertTrue(HandwritingCandidatePolicy.select(listOf(HandwritingCandidate("中", .1f)), 0).isEmpty())
    }
}
