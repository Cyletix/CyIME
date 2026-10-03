package com.kingzcheung.xime.association

import org.junit.Assert.*
import org.junit.Test

class ModelCandidateScoresTest {
    @Test fun logitsAcrossZeroPreserveRankingAndSumToOne() {
        val result = normalizeModelCandidates(listOf(
            AssociationCandidate("高", 2f), AssociationCandidate("中", 0f), AssociationCandidate("低", -1f)))
        assertTrue(result[0].score > result[1].score)
        assertTrue(result[1].score > result[2].score)
        assertEquals(1f, result.sumOf { it.score.toDouble() }.toFloat(), 0.00001f)
    }
    @Test fun constantLogitOffsetCannotChangeFusionWeights() {
        val input = listOf(AssociationCandidate("甲", -4f), AssociationCandidate("乙", 2f))
        val original = normalizeModelCandidates(input)
        val shifted = normalizeModelCandidates(input.map { it.copy(score = it.score + 1000f) })
        original.indices.forEach { assertEquals(original[it].score, shifted[it].score, 0.00001f) }
    }
    @Test fun invalidScoresAndBlankTokensCannotPoisonTheWholeList() {
        val result = normalizeModelCandidates(listOf(AssociationCandidate("", 4f),
            AssociationCandidate("坏", Float.NaN), AssociationCandidate("无穷", Float.POSITIVE_INFINITY),
            AssociationCandidate("正常", Float.MAX_VALUE)))
        assertEquals(listOf(AssociationCandidate("正常", 1f)), result)
        assertTrue(normalizeModelCandidates(emptyList()).isEmpty())
    }
}
