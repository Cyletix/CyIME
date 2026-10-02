package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.*
import org.junit.Assert.*
import org.junit.Test

class T9CandidateNavigationTest {
    private val profile = InputProfile(InputLanguage.CHINESE, InputScheme.PINYIN, InputLayout.T9,
        EngineProfile.Rime("actual-installed-backend"))
    private val composing = CandidateState(candidates = listOf("促使一样吗", "不是一样吗", "不是一样嘛"),
        candidateComments = listOf("cu shi yi yang ma", "bu shi yi yang ma", "bu shi yi yang ma"),
        inputText = "28744926462", preeditText = "cu'shi'yi'yang'ma", isComposing = true, engineRevision = 7)

    @Test fun nextMovesHighlightWithoutConsumingInputAndSpaceResolvesTheHighlightedCandidate() {
        val second = composing.advanceT9Candidate(profile)
        assertEquals(1, second.highlightedCandidateIndex)
        assertEquals("不是一样吗", second.candidates[second.spaceCandidateIndex(profile)])
        assertEquals(composing, second.copy(candidateFocus = null))
        assertTrue(composing.hasSameSelectionSource(second))
        val third = second.advanceT9Candidate(profile)
        assertEquals(2, third.spaceCandidateIndex(profile))
        assertEquals(0, third.advanceT9Candidate(profile).highlightedCandidateIndex)
        // Space captures an immutable selection before a subsequent Next tap.
        assertEquals(1, second.spaceCandidateIndex(profile))
    }

    @Test fun emptyAndSingleCandidateNeverAccidentallyCommitOrInsertZero() {
        val empty = composing.copy(candidates = emptyList(), candidateComments = emptyList())
        assertTrue(empty.usesT9CandidateNavigation(profile))
        assertSame(empty, empty.advanceT9Candidate(profile))
        assertSame(empty, empty.retreatT9Candidate(profile))
        val single = composing.copy(candidates = listOf("不是一样吗"))
        assertEquals(0, single.advanceT9Candidate(profile).spaceCandidateIndex(profile))
        assertEquals(0, single.retreatT9Candidate(profile).spaceCandidateIndex(profile))
    }

    @Test fun previousMovesOnlyTheHighlightAndPreservesTheSpaceSnapshot() {
        val third = composing.advanceT9Candidate(profile).advanceT9Candidate(profile)
        val second = third.retreatT9Candidate(profile)
        assertEquals(1, second.highlightedCandidateIndex)
        assertEquals(composing, second.copy(candidateFocus = null))
        assertEquals("不是一样吗", second.candidates[second.spaceCandidateIndex(profile)])
        assertEquals(0, second.retreatT9Candidate(profile).highlightedCandidateIndex)
        assertEquals(2, composing.retreatT9Candidate(profile).highlightedCandidateIndex)
        assertEquals(2, third.spaceCandidateIndex(profile))
    }

    @Test fun editingDeletingPartialCommitAndReorderedOrTransformedCandidatesResetFocus() {
        val selected = composing.advanceT9Candidate(profile)
        listOf(selected.copy(engineRevision = 8), selected.copy(inputText = "28744"),
            selected.copy(candidates = selected.candidates.reversed()),
            selected.copy(candidateComments = listOf("new reading")),
            selected.copy(candidateActions = listOf(CandidateAction.plugin("plugin text"))),
            selected.copy(isComposing = false), selected.copy(isShowingRecentClipboard = true),
            selected.copy(candidates = emptyList()))
            .forEach { assertEquals(0, it.highlightedCandidateIndex) }
        assertEquals(1, selected.copy(associationCandidates = listOf("无关联想")).highlightedCandidateIndex)
    }

    @Test fun idleClipboardAssociationsEnglishAndOtherLayoutsKeepTheirExistingZeroBehavior() {
        listOf(CandidateState(), CandidateState(associationCandidates = listOf("联想")),
            composing.copy(isComposing = false), composing.copy(inputText = ""),
            composing.copy(isShowingRecentClipboard = true), composing.copy(pendingEnglishText = "hello"))
            .forEach {
                assertFalse(it.usesT9CandidateNavigation(profile))
                assertSame(it, it.advanceT9Candidate(profile))
                assertSame(it, it.retreatT9Candidate(profile))
            }
        val focused = composing.advanceT9Candidate(profile)
        listOf(profile.copy(language = InputLanguage.ENGLISH), profile.copy(language = InputLanguage.JAPANESE),
            profile.copy(layout = InputLayout.NUMBER), profile.copy(layout = InputLayout.QWERTY),
            profile.copy(scheme = InputScheme.STROKE), profile.handwriting())
            .forEach {
                assertFalse(focused.usesT9CandidateNavigation(it))
                assertEquals(0, focused.spaceCandidateIndex(it))
                assertSame(focused, focused.retreatT9Candidate(it))
            }
    }

    @Test fun identicalLabelsWithDifferentCommitActionsKeepTheirDisplayIndices() {
        val state = composing.copy(candidates = listOf("一样", "一样"),
            candidateActions = listOf(CandidateAction.engine(4), CandidateAction.plugin("第二个真实文本")))
        val focused = state.advanceT9Candidate(profile)
        assertEquals(CandidateAction.plugin("第二个真实文本"), focused.candidateActions[focused.spaceCandidateIndex(profile)])
    }
}
