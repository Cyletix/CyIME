package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.InputCommandQueue
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.settings.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HardwareCandidateNavigationTest {
    private val profile = InputProfile(InputLanguage.CHINESE, InputScheme.PINYIN, InputLayout.T9,
        EngineProfile.Rime("installed-backend"))
    private val composition = CandidateState(inputText = "ceshi", isComposing = true,
        candidates = listOf("测试", "侧室", "测视"), engineRevision = 4L)
    private fun snapshot(state: CandidateState = composition, english: Boolean = false) =
        HardwareCandidateSnapshot(1, profile, state, english)

    @Test fun chineseAndJapaneseCompositionNavigateAndConfirmWithoutMovingTheEditor() {
        for (language in listOf(InputLanguage.CHINESE, InputLanguage.JAPANESE)) {
            val navigation = HardwareCandidateNavigation()
            val source = snapshot().copy(profile = profile.copy(language = language))
            assertEquals(HardwareCandidateDecision.Highlight(1), navigation.decide(HardwareCandidateKey.RIGHT, source, true))
            assertEquals(HardwareCandidateDecision.Confirm(1, false), navigation.decide(HardwareCandidateKey.SPACE, source, true))
            assertEquals(HardwareCandidateDecision.Highlight(0), navigation.decide(HardwareCandidateKey.LEFT, source, true))
            assertEquals(HardwareCandidateDecision.Confirm(0, false), navigation.decide(HardwareCandidateKey.SPACE, source, true))
        }
    }

    @Test fun ordinaryEnglishSpaceKeepsTypingBehaviorAndNavigatedSpaceChoosesCompletion() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot(CandidateState(pendingEnglishText = "tes",
            associationCandidates = listOf("test", "testing", "tests")), english = true)
        assertEquals(HardwareCandidateDecision.DefaultSpace, navigation.decide(HardwareCandidateKey.SPACE, source, false))
        assertEquals(HardwareCandidateDecision.Highlight(1), navigation.decide(HardwareCandidateKey.RIGHT, source, false))
        assertEquals(HardwareCandidateDecision.Confirm(1, true), navigation.decide(HardwareCandidateKey.SPACE, source, false))
        repeat(8) { navigation.decide(HardwareCandidateKey.RIGHT, source, false) }
        assertEquals(2, navigation.selectedIndex(source))
        assertEquals(HardwareCandidateDecision.Confirm(2, true), navigation.decide(HardwareCandidateKey.SPACE, source, false))
    }

    @Test fun chineseSuggestionsUseExplicitSelectionWithoutOverridingTheOrdinarySpaceSetting() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot(CandidateState(associationCandidates = listOf("世界", "朋友", "大家")))
        assertEquals(HardwareCandidateDecision.DefaultSpace, navigation.decide(HardwareCandidateKey.SPACE, source, false))
        navigation.decide(HardwareCandidateKey.RIGHT, source, false)
        assertEquals(HardwareCandidateDecision.Confirm(1, true), navigation.decide(HardwareCandidateKey.SPACE, source, false))
    }

    @Test fun pendingPredictionNeverNavigatesOrConfirmsOldWords() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot(CandidateState(associationCandidates = listOf("旧词", "旧候选")))
        navigation.decide(HardwareCandidateKey.RIGHT, source, false)
        val waiting = source.copy(predictionPending = true)
        assertEquals(HardwareCandidateDecision.Host, navigation.decide(HardwareCandidateKey.RIGHT, waiting, false))
        assertEquals(HardwareCandidateDecision.DefaultSpace, navigation.decide(HardwareCandidateKey.SPACE, waiting, false))
        assertNull(navigation.selectedIndex(source))
        assertEquals(HardwareCandidateDecision.Cancel, navigation.decide(HardwareCandidateKey.CANCEL, waiting, false))
    }

    @Test fun unrelatedPredictionCompletionDoesNotResetCompositionOrEnglishSelection() {
        for (source in listOf(snapshot(), snapshot(CandidateState(pendingEnglishText = "tes",
            associationCandidates = listOf("test")), true))) {
            val navigation = HardwareCandidateNavigation()
            val waiting = source.copy(predictionPending = true)
            navigation.decide(HardwareCandidateKey.RIGHT, waiting, !source.english)
            assertEquals(1, navigation.selectedIndex(source))
            assertEquals(HardwareCandidateDecision.Confirm(1, source.presentation.association),
                navigation.decide(HardwareCandidateKey.SPACE, source, !source.english))
        }
    }

    @Test fun unavailableEngineProtectsKnownCompositionWithoutDisablingAnIdleEditor() {
        val navigation = HardwareCandidateNavigation()
        val emptyUi = snapshot(CandidateState())
        for (key in listOf(HardwareCandidateKey.LEFT, HardwareCandidateKey.RIGHT,
            HardwareCandidateKey.UP, HardwareCandidateKey.DOWN, HardwareCandidateKey.SPACE)) {
            assertEquals(HardwareCandidateDecision.Consume, navigation.decide(key, emptyUi, true))
            assertEquals(if (key == HardwareCandidateKey.SPACE) HardwareCandidateDecision.DefaultSpace else HardwareCandidateDecision.Host,
                navigation.decide(key, emptyUi, null))
            assertEquals(HardwareCandidateDecision.Consume, navigation.decide(key, snapshot(), null))
        }
        assertEquals(HardwareCandidateDecision.Host, navigation.decide(HardwareCandidateKey.LEFT, emptyUi, false))
    }

    @Test fun changedCandidateIdentityCannotReuseAnOldSelection() {
        val source = snapshot()
        for (changed in listOf(
            source.copy(editor = 2),
            source.copy(profile = profile.copy(language = InputLanguage.JAPANESE)),
            source.copy(state = composition.copy(engineRevision = 5)),
            source.copy(state = composition.copy(inputText = "ce")),
            source.copy(state = composition.copy(candidates = composition.candidates.reversed())),
            source.copy(state = composition.copy(candidateComments = listOf("changed"))),
            source.copy(state = composition.copy(candidateActions = listOf(CandidateAction.plugin("replacement")))),
        )) {
            val navigation = HardwareCandidateNavigation()
            navigation.decide(HardwareCandidateKey.RIGHT, source, true)
            assertNull(navigation.selectedIndex(changed))
            assertEquals(HardwareCandidateDecision.Confirm(0, false), navigation.decide(HardwareCandidateKey.SPACE, changed, true))
            assertNull(navigation.selectedIndex(source))
        }
    }

    @Test fun newEnglishPrefixAndAssociationContextInvalidateExplicitSpaceSelection() {
        val source = snapshot(CandidateState(pendingEnglishText = "tes", associationCandidates = listOf("test")), true)
        for (changed in listOf(
            source.copy(state = source.state.copy(pendingEnglishText = "test")),
            source.copy(associationContext = "new surrounding context"),
        )) {
            val navigation = HardwareCandidateNavigation()
            navigation.decide(HardwareCandidateKey.RIGHT, source, false)
            assertEquals(HardwareCandidateDecision.DefaultSpace, navigation.decide(HardwareCandidateKey.SPACE, changed, false))
        }
    }

    @Test fun nativeSelectionIsLimitedToTenDisplayedCandidatesAndTouchArrowsRetainPaging() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot(composition.copy(candidates = (0..15).map { "词$it" }, hasNextPage = true, hasPrevPage = true))
        repeat(20) { navigation.decide(HardwareCandidateKey.RIGHT, source, true) }
        assertEquals(9, navigation.selectedIndex(source))
        assertEquals(HardwareCandidateDecision.Page(1), navigation.decide(HardwareCandidateKey.RIGHT, source, true, pageAtBoundary = true))
        assertEquals(HardwareCandidateDecision.Page(-1), navigation.decide(HardwareCandidateKey.LEFT, source, true, pageAtBoundary = true))
    }

    @Test fun escapeCancelsCompositionAndEnglishCandidatesBeforeArrowsCanMoveTheEditor() {
        for (source in listOf(snapshot(), snapshot(CandidateState(pendingEnglishText = "typed"), true))) {
            val navigation = HardwareCandidateNavigation()
            assertEquals(HardwareCandidateDecision.Cancel, navigation.decide(HardwareCandidateKey.CANCEL, source, !source.english))
            // Clearing pending text is a state operation, not a request to delete its already committed characters.
            val cleared = source.copy(state = CandidateState())
            assertEquals(HardwareCandidateDecision.Host, navigation.decide(HardwareCandidateKey.RIGHT, cleared, false))
        }
    }

    @Test fun heldCandidateArrowRemainsOwnedUntilReleaseEvenAfterCandidatesAreCancelled() {
        for (key in listOf(HardwareCandidateKey.LEFT, HardwareCandidateKey.RIGHT,
            HardwareCandidateKey.UP, HardwareCandidateKey.DOWN)) {
            val navigation = HardwareCandidateNavigation()
            navigation.startPress(key, repeat = false)
            assertNotEquals(HardwareCandidateDecision.Host, navigation.decide(key, snapshot(), true))
            navigation.retainPress(key)
            navigation.clear()
            navigation.startPress(key, repeat = true)
            assertEquals(HardwareCandidateDecision.Host, navigation.decide(key, snapshot(CandidateState()), false))
            assertTrue(navigation.ownsPress(key))
            navigation.releasePress(key)
            assertFalse(navigation.ownsPress(key))
            navigation.startPress(key, repeat = false)
            assertFalse(navigation.ownsPress(key))
        }
    }

    @Test fun sideDockHiddenSuggestionsDoNotOwnArrowsOrSelectInvisibleWords() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot(CandidateState(pendingEnglishText = "hel", associationCandidates = listOf("hello", "help")), true)
        navigation.decide(HardwareCandidateKey.RIGHT, source, false)
        val hidden = source.copy(associationLimit = 0)
        assertTrue(hidden.words.isEmpty())
        assertNull(navigation.selectedIndex(hidden))
        for (key in listOf(HardwareCandidateKey.LEFT, HardwareCandidateKey.RIGHT, HardwareCandidateKey.UP, HardwareCandidateKey.DOWN)) {
            assertEquals(HardwareCandidateDecision.Host, navigation.decide(key, hidden, false))
        }
        assertEquals(HardwareCandidateDecision.DefaultSpace, navigation.decide(HardwareCandidateKey.SPACE, hidden, false))
        assertEquals(HardwareCandidateDecision.Cancel, navigation.decide(HardwareCandidateKey.CANCEL, hidden, false))
    }

    @Test fun fullKeyboardCanNavigateAllItsEnglishSuggestions() {
        val navigation = HardwareCandidateNavigation()
        val state = CandidateState(pendingEnglishText = "te", associationCandidates = listOf("test", "text", "testing", "tests"))
        val full = snapshot(state, true).copy(associationLimit = Int.MAX_VALUE)
        assertEquals(listOf("te", "test", "text", "testing", "tests"), full.words)
        assertEquals(full.words, full.presentation.words)
        repeat(4) { navigation.decide(HardwareCandidateKey.RIGHT, full, false) }
        assertEquals(4, navigation.selectedIndex(full))
        assertEquals(HardwareCandidateDecision.Confirm(4, true), navigation.decide(HardwareCandidateKey.SPACE, full, false))
        assertNull(navigation.selectedIndex(full.copy(associationLimit = 3)))
    }

    @Test fun verticalArrowsNavigateAssociationsButPageNativeComposition() {
        val navigation = HardwareCandidateNavigation()
        val suggestions = snapshot(CandidateState(associationCandidates = listOf("世界", "朋友", "大家")))
        assertEquals(HardwareCandidateDecision.Highlight(1), navigation.decide(HardwareCandidateKey.DOWN, suggestions, false))
        assertEquals(HardwareCandidateDecision.Highlight(0), navigation.decide(HardwareCandidateKey.UP, suggestions, false))
        assertEquals(HardwareCandidateDecision.Consume, navigation.decide(HardwareCandidateKey.UP, snapshot(), true))
        assertEquals(HardwareCandidateDecision.Consume, navigation.decide(HardwareCandidateKey.DOWN, snapshot(), true))
        val middlePage = snapshot(composition.copy(hasPrevPage = true, hasNextPage = true))
        assertEquals(HardwareCandidateDecision.Page(-1), navigation.decide(HardwareCandidateKey.UP, middlePage, true))
        assertEquals(HardwareCandidateDecision.Page(1), navigation.decide(HardwareCandidateKey.DOWN, middlePage, true))
        assertNull(navigation.selectedIndex(middlePage))
    }

    @Test fun missingDefaultEngineActionsDoNotInvalidateAnUnchangedCandidateSelection() {
        val navigation = HardwareCandidateNavigation()
        val source = snapshot()
        navigation.decide(HardwareCandidateKey.RIGHT, source, true)
        val explicit = source.copy(state = composition.copy(candidateActions = composition.candidates.indices.map(CandidateAction::engine)))
        assertTrue(source.sameSource(explicit))
        assertEquals(1, navigation.selectedIndex(explicit))
        assertEquals(HardwareCandidateDecision.Confirm(1, false), navigation.decide(HardwareCandidateKey.SPACE, explicit, true))
        val partial = source.copy(state = composition.copy(candidateActions = listOf(CandidateAction.engine(0))))
        assertEquals(1, navigation.selectedIndex(partial))
        val changed = source.copy(state = composition.copy(candidateActions = listOf(CandidateAction.plugin("替换"))))
        assertNull(navigation.selectedIndex(changed))
    }

    @Test fun expandedCandidatesAreNotTruncatedAndKeepTheirGlobalCommitIndices() {
        val navigation = HardwareCandidateNavigation()
        val candidates = (0..17).map { RimeCandidate("候选$it", "编码$it") }
        val source = snapshot(composition.copy(expandedCandidates = candidates, expandedCandidatesLoaded = true)).copy(expanded = true)
        assertEquals(candidates.map { it.text }, source.words)
        assertEquals(candidates.map { it.comment }, source.comments)
        assertEquals(candidates.indices.toList(), source.expandedGlobalIndices)
        repeat(15) { navigation.decide(HardwareCandidateKey.RIGHT, source, true) }
        assertEquals(15, navigation.selectedIndex(source))
        assertEquals(HardwareCandidateDecision.Confirm(15, false), navigation.decide(HardwareCandidateKey.SPACE, source, true))
        assertEquals(15, source.expandedGlobalIndices!![15])
    }

    @Test fun expandedSingleCharacterFilterUsesCodePointsAndOriginalGlobalIndices() {
        val candidates = listOf(RimeCandidate("测试", "ce shi"), RimeCandidate("字", "zi"),
            RimeCandidate("𠀀", "supplementary"), RimeCandidate("词语", "ci yu"), RimeCandidate("词", "ci"))
        val source = snapshot(composition.copy(expandedCandidates = candidates, expandedCandidatesLoaded = true))
            .copy(expanded = true, singleCharOnly = true)
        assertEquals(listOf("字", "𠀀", "词"), source.words)
        assertEquals(listOf("zi", "supplementary", "ci"), source.comments)
        assertEquals(listOf(1, 2, 4), source.expandedGlobalIndices)
        val navigation = HardwareCandidateNavigation()
        assertEquals(HardwareCandidateDecision.Highlight(1), navigation.decide(HardwareCandidateKey.RIGHT, source, true))
        assertEquals(HardwareCandidateDecision.Confirm(1, false), navigation.decide(HardwareCandidateKey.SPACE, source, true))
        assertEquals(2, source.expandedGlobalIndices!![1])
        for (changed in listOf(
            source.copy(singleCharOnly = false),
            source.copy(expanded = false),
            source.copy(state = source.state.copy(expandedCandidatesLoaded = false)),
            source.copy(state = source.state.copy(expandedCandidates = candidates.reversed())),
            source.copy(state = source.state.copy(engineRevision = 5)),
            source.copy(editor = 2),
        )) assertNull(navigation.selectedIndex(changed))
    }

    @Test fun emptyExpandedFilterDoesNotFallBackToUnfilteredCandidatesOrTheEditor() {
        val source = snapshot(composition.copy(expandedCandidates = listOf(RimeCandidate("测试", "ce shi")),
            expandedCandidatesLoaded = true)).copy(expanded = true, singleCharOnly = true)
        assertEquals(emptyList<Int>(), source.expandedGlobalIndices)
        assertTrue(source.words.isEmpty())
        val navigation = HardwareCandidateNavigation()
        assertEquals(HardwareCandidateDecision.Consume, navigation.decide(HardwareCandidateKey.RIGHT, source, true))
        assertEquals(HardwareCandidateDecision.Consume, navigation.decide(HardwareCandidateKey.SPACE, source, true))
    }

    @Test fun expandedAppendKeepsTheSelectedGlobalCandidateAndSpaceConfirmation() {
        val candidates = (0 until 128).map { RimeCandidate((0x4E00 + it).toChar().toString(), "code$it") }
        for (singleChar in listOf(false, true)) {
            val initial = snapshot(composition.copy(expandedCandidates = candidates.take(64), expandedCandidatesLoaded = true))
                .copy(expanded = true, singleCharOnly = singleChar)
            val extended = initial.copy(state = initial.state.copy(expandedCandidates = candidates))
            val navigation = HardwareCandidateNavigation()
            navigation.decide(HardwareCandidateKey.RIGHT, initial, true)
            assertTrue(initial.sameSource(extended))
            assertFalse("Shrinking is not an append", extended.sameSource(initial))
            assertEquals(1, navigation.selectedIndex(extended))
            assertEquals(HardwareCandidateDecision.Confirm(1, false), navigation.decide(HardwareCandidateKey.SPACE, extended, true))
            assertEquals(1, extended.expandedGlobalIndices!![1])
            assertEquals(initial.words[1], extended.words[1])
            assertEquals(initial.comments[1], extended.comments[1])
        }
    }

    @Test fun expandedReorderShrinkOrChangedExistingCommentInvalidatesTheSelection() {
        val candidates = (0 until 64).map { RimeCandidate("词$it", "code$it") }
        val initial = snapshot(composition.copy(expandedCandidates = candidates, expandedCandidatesLoaded = true)).copy(expanded = true)
        for (replacement in listOf(candidates.reversed(), candidates.dropLast(1),
            candidates.toMutableList().also { it[1] = RimeCandidate(it[1].text, "changed") })) {
            val navigation = HardwareCandidateNavigation()
            navigation.decide(HardwareCandidateKey.RIGHT, initial, true)
            val changed = initial.copy(state = initial.state.copy(expandedCandidates = replacement))
            assertFalse(initial.sameSource(changed))
            assertNull(navigation.selectedIndex(changed))
        }
    }

    @Test fun expandedVerticalArrowsPageTheLocalViewportEvenAtNativePageBoundaries() {
        val source = snapshot(composition.copy(hasPrevPage = false, hasNextPage = false,
            expandedCandidates = listOf(RimeCandidate("测", "ce")), expandedCandidatesLoaded = true)).copy(expanded = true)
        val navigation = HardwareCandidateNavigation()
        assertEquals(HardwareCandidateDecision.Page(-1), navigation.decide(HardwareCandidateKey.UP, source, true))
        assertEquals(HardwareCandidateDecision.Page(1), navigation.decide(HardwareCandidateKey.DOWN, source, true))
    }

    @Test fun rapidLetterAndArrowAreDecidedInFifoOrderEvenWhileUiPublicationIsDelayed() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val letterStarted = CompletableDeferred<Unit>()
        val releaseLetter = CompletableDeferred<Unit>()
        var engineHasInput = false
        val oldEmptyUi = snapshot(CandidateState())
        var decision: HardwareCandidateDecision? = null
        try {
            queue.submit {
                letterStarted.complete(Unit)
                releaseLetter.await()
                engineHasInput = true
            }
            withTimeout(3000) { letterStarted.await() }
            val arrow = queue.submit {
                decision = HardwareCandidateNavigation().decide(HardwareCandidateKey.RIGHT, oldEmptyUi, engineHasInput)
            }
            assertFalse(arrow.isCompleted)
            releaseLetter.complete(Unit)
            withTimeout(3000) { arrow.join() }
            assertEquals(HardwareCandidateDecision.Consume, decision)
        } finally { queue.close() }
    }
}
