package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate
import org.junit.Assert.*
import org.junit.Test

class CandidateSelectionSourceTest {
    @Test fun appendAndSuggestionRefreshKeepExistingTapIdentityButReorderDoesNot() {
        val original = CandidateState(candidates = listOf("是", "时"), inputText = "74", engineRevision = 7,
            expandedCandidates = listOf(RimeCandidate("是", "shi")))
        assertTrue(original.hasSameSelectionSource(original.copy(associationCandidates = listOf("好的"))))
        assertTrue(original.hasSameSelectionSource(original.copy(expandedCandidates = original.expandedCandidates + RimeCandidate("时", "shi"))))
        assertFalse(original.hasSameSelectionSource(original.copy(engineRevision = 8)))
        assertFalse(original.hasSameSelectionSource(original.copy(candidates = listOf("时", "是"))))
        assertFalse(original.hasSameSelectionSource(original.copy(expandedCandidates = listOf(RimeCandidate("时", "shi")))))
    }
}
