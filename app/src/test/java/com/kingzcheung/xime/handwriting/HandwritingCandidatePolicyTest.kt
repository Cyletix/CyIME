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

    private fun han(vararg c: Pair<String, Float>) = c.map { HandwritingCandidate(it.first, it.second) }

    @Test fun rareTemplateNeverTakesConfidentCommonCharacterOrTopThree() {
        val weights = mapOf("日" to 70124, "目" to 1983, "曰" to 2224, "旦" to 3000)
        val model = han("日" to .9f, "曰" to .04f, "目" to .03f, "旦" to .01f)
        // 生僻字模板很像（0.9）也只有 0.5×0.81=0.405，排不过 0.9 的“日”；没把握的排在前三之后
        val merged = HandwritingCandidatePolicy.mergeRare(model, listOf("𣅀" to .9f), weights)
        assertEquals(listOf("日", "曰", "目", "𣅀", "旦"), merged.map { it.char })
    }

    @Test fun rareTemplateGoesFirstWhenModelHasNoGoodAnswer() {
        val weights = mapOf("奔" to 9000, "牛" to 20000, "犁" to 900)
        // 写的是犇：模型在几个字之间摇摆，模板几乎一样
        val model = han("奔" to .2f, "牛" to .15f, "犁" to .1f)
        val merged = HandwritingCandidatePolicy.mergeRare(model, listOf("犇" to .97f), weights)
        assertEquals("犇", merged.first().char)
        // 分数按原位置保留：叠写切分只看首位分数，不受影响
        assertEquals(listOf(.2f, .15f, .1f, .1f), merged.map { it.score })
    }

    @Test fun weakOrDuplicateRareMatchesAreIgnored() {
        val model = han("中" to .6f, "申" to .2f)
        assertEquals(listOf("中", "申"), HandwritingCandidatePolicy.mergeRare(model, listOf("㐬" to .5f), emptyMap()).map { it.char })
        assertEquals(listOf("中", "申"), HandwritingCandidatePolicy.mergeRare(model, listOf("申" to .99f), emptyMap()).map { it.char })
        assertEquals(HandwritingCandidatePolicy.preferCommon(model, emptyMap()),
            HandwritingCandidatePolicy.mergeRare(model, emptyList(), emptyMap()))
    }

    @Test fun atMostFourRareCharactersAreAdded() {
        val model = han("中" to .9f, "申" to .05f, "甲" to .03f, "由" to .01f)
        val rare = listOf("㐀" to .8f, "㐁" to .8f, "㐂" to .8f, "㐃" to .8f, "㐄" to .8f)
        val merged = HandwritingCandidatePolicy.mergeRare(model, rare, emptyMap())
        assertEquals(4, merged.count { it.char in rare.map { r -> r.first } })
        assertEquals(listOf("中", "申", "甲"), merged.take(3).map { it.char })
    }
}
