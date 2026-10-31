package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class EnglishLiteralInputTest {
    @Test fun lettersCommitLiterallyEvenWithStaleChineseDecoderCandidates() {
        for (word in listOf("hello", "fuck", "you", "Hello")) {
            var state = CandidateState(candidates = listOf("核力量哦"), isComposing = true, inputText = "old")
            val editor = StringBuilder()
            for (letter in word) {
                state = requireNotNull(commitEnglishLetter(state, letter.toString(), letter.isUpperCase(), { true }) {
                    editor.append(it); true
                })
            }
            assertEquals(word, editor.toString())
            assertEquals(word, state.pendingEnglishText)
            assertFalse(state.isComposing)
            assertTrue(state.candidates.isEmpty())
        }
    }

    @Test fun letterRightAfterAGlidedWordStartsANewWordWithASpace() {
        val glided = CandidateState(pendingEnglishText = "hello", glidedWord = "hello", associationCandidates = listOf("hero"))
        val editor = StringBuilder("hello")
        val next = requireNotNull(commitEnglishLetter(glided, "w", false, { true }, afterGlide = true) {
            editor.append(it); true
        })
        assertEquals("hello w", editor.toString())
        assertEquals("w", next.pendingEnglishText)
        assertEquals("", next.glidedWord)
        // 之后的字母照常接在新词后面，不再补空格。
        val after = requireNotNull(commitEnglishLetter(next, "o", false, { true }) { editor.append(it); true })
        assertEquals("hello wo", editor.toString())
        assertEquals("wo", after.pendingEnglishText)
    }

    @Test fun rejectedLetterDoesNotAdvanceThePendingWord() {
        val state = CandidateState(pendingEnglishText = "hell")
        assertNull(commitEnglishLetter(state, "o", false, { error("Rejected input must not probe the editor") }) { false })
        assertEquals("hell", state.pendingEnglishText)
    }

    @Test fun letterIsDeliveredBeforeOptionalCompletionProbe() {
        val events = mutableListOf<String>()
        val next = commitEnglishLetter(CandidateState(), "a", false,
            replaceSupported = { events += "probe"; false },
            commit = { events += "commit:$it"; true })
        assertEquals(listOf("commit:a", "probe"), events)
        assertEquals("a", next?.pendingEnglishText)
        assertEquals(false, next?.englishReplaceSupported)
    }

    @Test fun rapidLiteralInputDoesNotQueryTheHostPerLetter() {
        val capability = EditorReadCapability()
        val connection = Any()
        var probes = 0
        var state = CandidateState()
        val editor = StringBuilder()
        val text = "qwertyuiopasdfghjklzxcvbnm".repeat(20)
        text.forEach { letter ->
            state = requireNotNull(commitEnglishLetter(state, letter.toString(), false,
                replaceSupported = { capability.supports(1L, connection) { probes++; true } },
                commit = { editor.append(it); true }))
        }
        assertEquals(text, editor.toString())
        assertEquals(text, state.pendingEnglishText)
        assertEquals(1, probes)
    }

    @Test fun spanishLettersJoinTheWordOnlyOnTheSpanishKeyboard() {
        for (letter in listOf("ñ", "á", "é", "í", "ó", "ú", "ü", "Ñ", "É")) {
            assertTrue(letter, isDirectLetter(letter, spanish = true))
            assertFalse(letter, isDirectLetter(letter, spanish = false))
        }
        for (key in listOf("a", "Z")) assertTrue(key, isDirectLetter(key, spanish = false))
        for (key in listOf("¿", ",", "1", "space", "è", "")) assertFalse(key, isDirectLetter(key, spanish = true))
        var state = CandidateState()
        val editor = StringBuilder()
        for ((letter, shifted) in listOf("n" to true, "i" to false, "ñ" to false, "o" to false)) {
            state = requireNotNull(commitEnglishLetter(state, letter, shifted, { true }) { editor.append(it); true })
        }
        assertEquals("Niño", editor.toString())
        assertEquals("Niño", state.pendingEnglishText)
    }
}
