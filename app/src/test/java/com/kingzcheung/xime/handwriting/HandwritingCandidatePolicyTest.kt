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
    @Test fun closeRareCharacterYieldsToCommonOneButKeepsScoreSlots() {
        val weights = mapOf("日" to 70124, "曰" to 2224, "目" to 1983)
        val ranked = HandwritingCandidatePolicy.preferCommon(listOf(HandwritingCandidate("曰", .55f),
            HandwritingCandidate("日", .35f), HandwritingCandidate("目", .05f)), weights)
        assertEquals(listOf("日", "曰", "目"), ranked.map { it.char })
        // 切分只看首位分数：分数按原位置保留
        assertEquals(listOf(.55f, .35f, .05f), ranked.map { it.score })
    }
    @Test fun confidentRareCharacterStaysFirst() {
        val weights = mapOf("的" to 4829883, "砺" to 30)
        val ranked = HandwritingCandidatePolicy.preferCommon(listOf(HandwritingCandidate("砺", .9f),
            HandwritingCandidate("的", .01f)), weights)
        assertEquals("砺", ranked.first().char)
        assertEquals(ranked.map { it.char }, HandwritingCandidatePolicy.preferCommon(ranked, emptyMap()).map { it.char })
    }
    @Test fun candidateUnderHalfTheTopScoreNeverMovesAhead() {
        val weights = mapOf("的" to 4829883, "曰" to 2224)
        val ranked = HandwritingCandidatePolicy.preferCommon(listOf(HandwritingCandidate("曰", .6f),
            HandwritingCandidate("的", .29f)), weights)
        assertEquals(listOf("曰", "的"), ranked.map { it.char })
    }
    @Test fun emptyOrInvalidCandidatesDoNotRefillWithSymbols() {
        assertTrue(HandwritingCandidatePolicy.select(listOf(HandwritingCandidate("!", .9f), HandwritingCandidate("中", Float.NaN)), 5).isEmpty())
        assertTrue(HandwritingCandidatePolicy.select(listOf(HandwritingCandidate("中", .1f)), 0).isEmpty())
    }
}
