package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class EnglishLiteralInputTest {
    @Test fun sentenceCapsHonorsParagraphResponsesWithoutRequestingWordCapitalization() {
        val app = mock<android.content.Context>()
        val prefs = mock<android.content.SharedPreferences>()
        val editor = mock<android.view.inputmethod.InputConnection>()
        whenever(app.getSharedPreferences("kime_settings", 0)).thenReturn(prefs)
        assertFalse(englishSentenceStart(app, editor))
        verifyNoInteractions(editor)
        whenever(prefs.getBoolean(ENGLISH_SENTENCE_CAPS, false)).thenReturn(true)
        val mode = android.text.TextUtils.CAP_MODE_SENTENCES
        whenever(editor.getCursorCapsMode(mode)).thenReturn(android.text.TextUtils.CAP_MODE_WORDS, mode, 0)
        assertTrue(englishSentenceStart(app, editor))
        assertTrue(englishSentenceStart(app, editor))
        assertFalse(englishSentenceStart(app, editor))
        whenever(editor.getCursorCapsMode(mode)).thenThrow(IllegalStateException("closed editor"))
        assertFalse(englishSentenceStart(app, editor))
    }

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
        val spanish = com.kingzcheung.xime.settings.LetterLanguage.SPANISH.extraLetters
        for (letter in listOf("ñ", "á", "é", "í", "ó", "ú", "ü", "Ñ", "É")) {
            assertTrue(letter, isDirectLetter(letter, spanish))
            assertFalse(letter, isDirectLetter(letter, ""))
        }
        for (key in listOf("a", "Z")) assertTrue(key, isDirectLetter(key, ""))
        for (key in listOf("¿", ",", "1", "space", "è", "")) assertFalse(key, isDirectLetter(key, spanish))
        var state = CandidateState()
        val editor = StringBuilder()
        for ((letter, shifted) in listOf("n" to true, "i" to false, "ñ" to false, "o" to false)) {
            state = requireNotNull(commitEnglishLetter(state, letter, shifted, { true }) { editor.append(it); true })
        }
        assertEquals("Niño", editor.toString())
        assertEquals("Niño", state.pendingEnglishText)
    }

    @Test fun germanSharpSStaysSharpUnderShiftAndCyrillicTypesDirectly() {
        assertTrue(isDirectLetter("ß", com.kingzcheung.xime.settings.LetterLanguage.GERMAN.extraLetters))
        assertTrue(isDirectLetter("й", com.kingzcheung.xime.settings.LetterLanguage.RUSSIAN.extraLetters))
        assertFalse(isDirectLetter("й", com.kingzcheung.xime.settings.LetterLanguage.GERMAN.extraLetters))
        var state = CandidateState()
        val editor = StringBuilder()
        // ß 的 uppercase 是 SS：Shift 时照旧打一个 ß。
        for ((letter, shifted) in listOf("ß" to true, "п" to true, "р" to false)) {
            state = requireNotNull(commitEnglishLetter(state, letter, shifted, { true }) { editor.append(it); true })
        }
        assertEquals("ßПр", editor.toString())
    }
}
