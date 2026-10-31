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
        // 末个再「下一项」停在末个，不绕回首选（用户 10-31）。
        assertEquals(2, third.advanceT9Candidate(profile).highlightedCandidateIndex)
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
        // 首选再左滑「上一项」停在首选，不跳到队尾（用户 10-31）。
        assertSame(composing, composing.retreatT9Candidate(profile))
        assertEquals(0, second.retreatT9Candidate(profile).retreatT9Candidate(profile).highlightedCandidateIndex)
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
        listOf(CandidateState(),
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

    @Test fun predictionArrowFocusesFirstStopsAtBothEndsAndNewResultsInvalidateFocus() {
        val state = CandidateState(associationCandidates = listOf("世界", "朋友", "大家"))
        assertTrue(state.usesT9CandidateNavigation(profile))
        assertNull(state.focusedT9AssociationIndex)
        val first = state.advanceT9Candidate(profile)
        assertEquals(0, first.focusedT9AssociationIndex)
        val second = first.advanceT9Candidate(profile)
        assertEquals(1, second.spaceCandidateIndex(profile))
        assertEquals(0, second.retreatT9Candidate(profile).focusedT9AssociationIndex)
        // 联想没高亮时左滑不跳到队尾；高亮在两头时也不绕到另一头。
        assertSame(state, state.retreatT9Candidate(profile))
        val first0 = second.retreatT9Candidate(profile)
        assertSame(first0, first0.retreatT9Candidate(profile))
        val last = second.advanceT9Candidate(profile)
        assertEquals(2, last.focusedT9AssociationIndex)
        assertSame(last, last.advanceT9Candidate(profile))
        assertNull(second.copy(associationCandidates = listOf("新词")).focusedT9AssociationIndex)
        assertFalse(state.copy(pendingEnglishText = "hello").usesT9CandidateNavigation(profile))
        assertFalse(CandidateState().usesT9CandidateNavigation(profile))
    }

    @Test fun identicalLabelsWithDifferentCommitActionsKeepTheirDisplayIndices() {
        val state = composing.copy(candidates = listOf("一样", "一样"),
            candidateActions = listOf(CandidateAction.engine(4), CandidateAction.plugin("第二个真实文本")))
        val focused = state.advanceT9Candidate(profile)
        assertEquals(CandidateAction.plugin("第二个真实文本"), focused.candidateActions[focused.spaceCandidateIndex(profile)])
    }

    // 用户 10-27 截图：736 显示 pen、左栏 pen 在前、首选 盆，「下一项」高亮到 人 后编码和左栏却没跟着变。
    private val pen = CandidateState(candidates = listOf("盆", "人", "认", "SEO"),
        candidateComments = listOf("pen", "ren", "ren", ""), inputText = "pen", preeditText = "pen",
        isComposing = true, engineRevision = 3)

    @Test fun shownPreeditAndColumnFollowTheHighlightedCandidateThatSpaceCommits() {
        assertEquals("pen", pen.shownPreedit(profile))
        assertNull(pen.highlightedT9Syllable(profile))
        val ren = pen.advanceT9Candidate(profile)
        assertEquals("人", ren.candidates[ren.spaceCandidateIndex(profile)])
        assertEquals("ren", ren.shownPreedit(profile))
        assertEquals("ren", ren.highlightedT9Syllable(profile))
        // 英文候选没有注释，用它本身的字母。
        val seo = ren.advanceT9Candidate(profile).advanceT9Candidate(profile)
        assertEquals("seo", seo.shownPreedit(profile))
        // 末个不绕回首选；退回首选，又是引擎的编码；回车按屏上编码上屏。
        assertEquals("seo", seo.advanceT9Candidate(profile).shownPreedit(profile))
        assertEquals("pen", seo.retreatT9Candidate(profile).retreatT9Candidate(profile).retreatT9Candidate(profile)
            .shownPreedit(profile))
        assertEquals("ren", rawCompositionCommitText("736", ren.shownPreedit(profile), t9 = true))
        // 编码一变，高亮回首选，显示回到引擎编码。
        assertEquals("pen", ren.copy(engineRevision = 4).shownPreedit(profile))
    }

    @Test fun replacingOnlyThePartTheHighlightedCandidateUses() {
        // 同键不同音：替换它用掉的字母，后面没用掉的编码和已选的汉字不动。
        assertEquals("mi'hao", t9PreeditWithReading("ni'hao", "mi"))
        assertEquals("mi gao", t9PreeditWithReading("ni hao", "mi gao"))
        assertEquals("你好ce'shi", t9PreeditWithReading("你好ce'shi", "ce shi"))
        assertEquals("你好de'shi", t9PreeditWithReading("你好ce'shi", "de"))
        // 简拼：按键对不上，按音节数替换。
        assertEquals("ni'hao", t9PreeditWithReading("n'h", "ni hao"))
        assertNull(t9PreeditWithReading("n", "ni hao"))
        assertNull(t9PreeditWithReading("你好", "ni"))
    }

    @Test fun otherLayoutsKeepTheEnginePreedit() {
        val full = InputProfile(InputLanguage.CHINESE, InputScheme.PINYIN, InputLayout.QWERTY, EngineProfile.Rime("rime_ice"))
        assertEquals("pen", pen.copy(candidateFocus = CandidateFocus.at(1, pen)).shownPreedit(full))
        assertNull(pen.copy(candidateFocus = CandidateFocus.at(1, pen)).highlightedT9Syllable(full))
    }
}
