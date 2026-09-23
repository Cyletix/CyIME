package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KeyboardInputPreferencesTest {
    private val context = mock<Context>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer {
            values[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1)
        }
        whenever(prefs.getFloat(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Float ?: it.getArgument<Float>(1)
        }
        whenever(editor.putString(any(), anyOrNull())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value != null) values[it.getArgument(0)] = value
            editor
        }
        whenever(editor.putFloat(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Float>(1)
            editor
        }
    }

    @Test
    fun `existing installs default to cursor hold and usable sensitivity`() {
        val settings = KeyboardInputPreferences.read(context)
        assertEquals(SpaceHoldAction.CURSOR, settings.spaceHold)
        assertEquals(10f, settings.cursorStepDp, 0f)
        assertEquals(1.15f, settings.keyTextScale, 0f)
        assertEquals(0.5f, settings.handwritingPauseSeconds, 0f)
        assertNull(settings.symbols())
    }

    @Test
    fun `saved input preferences can be read without restarting the app`() {
        val changed = KeyboardInputPreferences(SpaceHoldAction.REPEAT, 6f, 1.4f, "…\n→")
        changed.save(context)
        assertEquals(changed, KeyboardInputPreferences.read(context))
        changed.copy(spaceHold = SpaceHoldAction.CURSOR, cursorStepDp = 24f).save(context)
        assertEquals(SpaceHoldAction.CURSOR, KeyboardInputPreferences.read(context).spaceHold)
        assertEquals(24f, KeyboardInputPreferences.read(context).cursorStepDp, 0f)
    }

    @Test
    fun `legacy voice hold migrates to cursor`() {
        values["space_hold_action"] = "VOICE"
        assertEquals(SpaceHoldAction.CURSOR, KeyboardInputPreferences.read(context).spaceHold)
    }

    @Test
    fun `unknown modes and invalid numeric settings recover to usable defaults`() {
        values["space_hold_action"] = "REMOVED_MODE"
        values["cursor_step_dp"] = Float.NaN
        values["key_text_scale"] = Float.POSITIVE_INFINITY
        val settings = KeyboardInputPreferences.read(context)
        assertEquals(SpaceHoldAction.CURSOR, settings.spaceHold)
        assertEquals(10f, settings.cursorStepDp, 0f)
        assertEquals(1.15f, settings.keyTextScale, 0f)
    }

    @Test
    fun `fixed symbols preserve phrases and emoji while removing blank duplicate lines`() {
        val settings = KeyboardInputPreferences(fixedSymbols = " … \n\n👨‍👩‍👧‍👦\n自定义短语\n…\n")
        assertEquals(listOf("…", "👨‍👩‍👧‍👦", "自定义短语"), settings.symbols())
        assertNull(settings.copy(fixedSymbols = " \n \n").symbols())
    }
    @Test fun `handwriting pause persists and rejects invalid intervals`() {
        KeyboardInputPreferences(handwritingPauseSeconds = 1.8f).save(context)
        assertEquals(1.8f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
        for ((stored, expected) in listOf(Float.NaN to 0.5f, -1f to 0.1f, 9f to 2.5f)) {
            values["handwriting_pause_seconds_v2"] = stored
            assertEquals(expected, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
        }
    }

    @Test fun `legacy one second default migrates but new explicit one second persists`() {
        values["handwriting_pause_seconds"] = 1f
        assertEquals(0.5f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
        KeyboardInputPreferences(handwritingPauseSeconds = 1f).save(context)
        assertEquals(1f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
    }
    @Test fun `handwriting delay saves tenths with a half second default`() {
        for (tenth in 1..25) {
            val value = tenth / 10f
            KeyboardInputPreferences(handwritingPauseSeconds = value).save(context)
            assertEquals(value, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
        }
        KeyboardInputPreferences(handwritingPauseSeconds = 0.56f).save(context)
        assertEquals(0.6f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f)
    }

}
