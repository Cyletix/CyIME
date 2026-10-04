package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class EnglishLiteralInputTest {
    @Test fun lettersCommitLiterallyEvenWithStaleChineseDecoderCandidates() {
        for (word in listOf("hello", "fuck", "you", "Hello")) {
            var state = CandidateState(candidates = listOf("核力量哦"), isComposing = true, inputText = "old")
            val editor = StringBuilder()
            for (letter in word) {
                state = requireNotNull(commitEnglishLetter(state, letter.toString(), letter.isUpperCase(), true) {
                    editor.append(it); true
                })
            }
            assertEquals(word, editor.toString())
            assertEquals(word, state.pendingEnglishText)
            assertFalse(state.isComposing)
            assertTrue(state.candidates.isEmpty())
        }
    }

    @Test fun rejectedLetterDoesNotAdvanceThePendingWord() {
        val state = CandidateState(pendingEnglishText = "hell")
        assertNull(commitEnglishLetter(state, "o", false, true) { false })
        assertEquals("hell", state.pendingEnglishText)
    }
}
