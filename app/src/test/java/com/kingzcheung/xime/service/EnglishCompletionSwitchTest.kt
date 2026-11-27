package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EnglishCompletionSwitchTest {
    @Test fun englishCompletionOffWithSmartPredictionOn() {
        val state = typeHel(smartPrediction = true, englishCompletion = false)

        assertTrue(state.englishCompletionOff)
        assertFalse(state.showsTypedEnglishWord)
        assertTrue(state.associationCandidates.isEmpty())
    }

    @Test fun bothSwitchesOnKeepTheTypedWordAndItsCompletions() {
        val state = typeHel(smartPrediction = true, englishCompletion = true)

        assertFalse(state.englishCompletionOff)
        assertTrue(state.showsTypedEnglishWord)
        assertEquals(listOf("help", "hello"), state.associationCandidates)
    }

    @Test fun smartPredictionOffOverridesEnglishCompletion() {
        val state = typeHel(smartPrediction = false, englishCompletion = true)

        assertTrue(state.englishCompletionOff)
        assertFalse(state.showsTypedEnglishWord)
        assertTrue(state.associationCandidates.isEmpty())
        assertEquals(typeHel(smartPrediction = false, englishCompletion = false), state)
    }

    @Test fun missingEnglishCompletionPreferenceKeepsCompletionsOn() {
        val state = typeHel(smartPrediction = true, englishCompletion = null)

        assertFalse(state.englishCompletionOff)
        assertEquals(listOf("help", "hello"), state.associationCandidates)
    }

    private fun typeHel(smartPrediction: Boolean, englishCompletion: Boolean?): CandidateState {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getBoolean(any(), any())).thenAnswer { it.getArgument<Boolean>(1) }
        whenever(prefs.getBoolean(SettingsPreferences.KEY_SMART_PREDICTION_ENABLED, true)).thenReturn(smartPrediction)
        if (englishCompletion != null) {
            whenever(prefs.getBoolean(SettingsPreferences.KEY_ENGLISH_COMPLETION_ENABLED, true)).thenReturn(englishCompletion)
        }
        val context = mock<Context>()
        whenever(context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)).thenReturn(prefs)

        val completionOn = SettingsPreferences.shouldCompleteEnglishWords(context)
        val committed = StringBuilder()
        var state = CandidateState()
        for (letter in "hel") {
            // 模拟上一次查询已返回，确保关掉补全后也不会沿用旧候选。
            if (letter == 'l') state = state.copy(associationCandidates = listOf("help", "hello"))
            state = requireNotNull(commitEnglishLetter(state, letter.toString(), false, { true }, completionOn = completionOn) {
                committed.append(it)
                true
            })
        }
        assertEquals("hel", committed.toString())
        return state
    }
}
