package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class HardwareCandidatePresentationTest {
    @Test fun pendingChinesePredictionHidesOldWordsUntilTheRequestFinishes() {
        val state = CandidateState(associationCandidates = listOf("旧联想"))
        val waiting = hardwareCandidatePresentation(state, false, predictionPending = true)
        assertTrue(waiting.association)
        assertTrue(waiting.words.isEmpty())
        assertEquals(listOf("新联想"), hardwareCandidatePresentation(
            state.copy(associationCandidates = listOf("新联想")), false).words)
        assertTrue(hardwareCandidatePresentation(state.copy(associationCandidates = emptyList()), false).words.isEmpty())
    }

    @Test fun pendingPredictionDoesNotHideCompositionOrEnglishCompletion() {
        assertEquals(listOf("测试"), hardwareCandidatePresentation(CandidateState(
            inputText = "ceshi", candidates = listOf("测试")), false, predictionPending = true).words)
        assertEquals(listOf("tes", "test"), hardwareCandidatePresentation(CandidateState(
            pendingEnglishText = "tes", associationCandidates = listOf("test")), true, predictionPending = true).words)
    }

    @Test fun englishUsesTheSameTypedWordAndCompletionIndicesAsScreenKeyboard() {
        val result = hardwareCandidatePresentation(CandidateState(pendingEnglishText = "tes",
            associationCandidates = listOf("test", "testing", "tests")), true)
        assertEquals(listOf("tes", "test", "testing"), result.words)
        assertTrue(result.association)
        assertTrue(result.standaloneWords.isEmpty())
        assertFalse(result.hasNextPage)
    }
    @Test fun idleEnglishNeverExposesStaleChinesePagesOrClipboard() {
        val result = hardwareCandidatePresentation(CandidateState(candidates = listOf("旧候选"),
            hasNextPage = true, hasPrevPage = true, isShowingRecentClipboard = true), true)
        assertTrue(result.words.isEmpty())
        assertFalse(result.hasNextPage)
        assertFalse(result.hasPreviousPage)
    }
    @Test fun unsupportedReplacementDoesNotOfferTypedTextAsAReplacement() {
        assertTrue(hardwareCandidatePresentation(CandidateState(pendingEnglishText = "secret",
            englishReplaceSupported = false), true).words.isEmpty())
    }
    @Test fun chineseCompositionRetainsEnginePagingAndComments() {
        val result = hardwareCandidatePresentation(CandidateState(candidates = listOf("敬酒"),
            candidateComments = listOf("jing jiu"), inputText = "jingjiu", hasNextPage = true), false)
        assertFalse(result.association)
        assertEquals(result.words, result.standaloneWords)
        assertTrue(result.hasNextPage)
        assertEquals(listOf("jing jiu"), result.comments)
    }

    @Test fun chineseSuggestionsStayInTheToolbarAndDoNotCreateACaretWindow() {
        val result = hardwareCandidatePresentation(CandidateState(
            associationCandidates = listOf("你好", "世界", "朋友")), false)
        assertEquals(listOf("你好", "世界", "朋友"), result.words)
        assertTrue(result.association)
        assertTrue(result.standaloneWords.isEmpty())
    }
}
