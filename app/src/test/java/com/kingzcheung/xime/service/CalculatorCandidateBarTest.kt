package com.kingzcheung.xime.service

import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub #289: a long calculator result was shown as typing candidates, with an expand page that had nothing in it. */
class CalculatorCandidateBarTest {
    private val long = listOf("121932631112635269", "123456789*987654321 = 121932631112635269")

    private fun bar(state: CandidateState) = CandidateBarState.from(
        candidates = state.candidates, candidateComments = state.candidateComments, inputText = state.inputText,
        isComposing = state.isComposing, associationCandidates = state.associationCandidates,
        isShowingRecentClipboard = false, hasNextPage = state.hasNextPage, isCalculatorActive = state.showsCalculator)

    @Test fun calculatorResultsUseTheCalculatorBarWithoutExpand() {
        val state = CandidateState(candidates = long, calculatorCandidates = long)
        assertTrue(state.showsCalculator)
        assertEquals(CandidateBarState.Calculator(candidates = long), bar(state))
    }

    @Test fun otherCandidatesAfterTheCalculatorAreNotTreatedAsResults() {
        val stale = CandidateState(candidates = listOf("你好", "拟好"), isComposing = true, calculatorCandidates = long)
        assertFalse(stale.showsCalculator)
        assertTrue(bar(stale) is CandidateBarState.ChineseCandidates)
        assertFalse(CandidateState(candidates = long).showsCalculator)
    }
}
