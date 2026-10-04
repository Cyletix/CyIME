package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.KeyEffectPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KeyboardInputPreferencesTest {
    @Test fun `symbol direction defaults to pulling into key and persists either habit`() {
        assertEquals(false, KeyboardInputPreferences.read(context).reverseSymbolSwipe)
        for (reversed in listOf(true, false)) {
            val before = KeyboardInputPreferences.read(context)
            before.copy(reverseSymbolSwipe = reversed).save(context)
            val after = KeyboardInputPreferences.read(context)
            assertEquals(reversed, after.reverseSymbolSwipe)
            assertEquals(before, after.copy(reverseSymbolSwipe = before.reverseSymbolSwipe))
        }
    }
    @Test fun `fourteen swipe preferences reload without changing shared cursor or symbol settings`() {
        val previous = KeyboardInputPreferences.read(context)
        assertEquals(true, previous.fourteenLetterSwipe)
        assertEquals(24f, previous.fourteenLetterSwipeDp, 0f)
        previous.copy(fourteenLetterSwipe = false, fourteenLetterSwipeDp = 51f).save(context)
        val restored = KeyboardInputPreferences.read(context)
        assertEquals(false, restored.fourteenLetterSwipe)
        assertEquals(52f, restored.fourteenLetterSwipeDp, 0f)
        assertEquals(previous.cursorGesture, restored.cursorGesture)
        assertEquals(previous.symbolInputMode, restored.symbolInputMode)
        assertEquals(previous.neighborCorrection, restored.neighborCorrection)
    }

    private val context = mock<Context>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
        whenever(prefs.getInt(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Int ?: it.getArgument<Int>(1)
        }
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer {
            values[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1)
        }
        whenever(prefs.getBoolean(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
        }
        whenever(prefs.getFloat(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Float ?: it.getArgument<Float>(1)
        }
        whenever(editor.putString(any(), anyOrNull())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value != null) values[it.getArgument(0)] = value
            editor
        }
        whenever(editor.putBoolean(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Boolean>(1)
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
        assertEquals(0.2f, settings.cursorHoldSeconds, 0f)
        assertEquals(200L, settings.spaceHoldDelayMs)
        assertEquals(10f, settings.cursorStepDp, 0f)
        assertEquals(1.15f, settings.keyTextScale, 0f)
        assertEquals(0.5f, settings.handwritingPauseSeconds, 0f)
        assertNull(settings.symbols())
    }

    @Test fun `visual effects are opt in and explicit choices are respected`() {
        assertEquals(false, KeyboardInputPreferences.read(context).keyGlowEnabled)
        assertEquals(false, KeyboardInputPreferences.read(context).showPressBubble)
        values["key_glow_enabled"] = true
        values[SettingsPreferences.KEY_SHOW_PRESS_BUBBLE] = true
        assertEquals(true, KeyboardInputPreferences.read(context).keyGlowEnabled)
        assertEquals(true, KeyboardInputPreferences.read(context).showPressBubble)
    }

    @Test fun `frosted glass is opt in and survives unrelated input preference saves`() {
        assertEquals(FrostedGlassConfig(), KeyboardInputPreferences.read(context).frostedGlass)
        val effect = FrostedGlassConfig(true, 18f, 0.7f, 0.3f)
        FrostedGlassPreferences.save(context, effect)
        assertEquals(effect, KeyboardInputPreferences.read(context).frostedGlass)
        KeyboardInputPreferences(spaceHold = SpaceHoldAction.REPEAT).save(context)
        assertEquals(effect, KeyboardInputPreferences.read(context).frostedGlass)
        assertEquals(false, KeyboardInputPreferences.read(context).keyGlowEnabled)
    }

    @Test fun `rendered appearance chooses its own glass controls despite saved display mode`() {
        values["dark_mode"] = 0
        val dark = FrostedGlassConfig(true, 15f, 0.77f, 0.21f)
        val light = FrostedGlassConfig(true, 11f, 0.24f, 0.84f)
        FrostedGlassPreferences.save(context, dark, true)
        FrostedGlassPreferences.save(context, light, false)
        assertEquals(dark, KeyboardInputPreferences.read(context, true).frostedGlass)
        assertEquals(light, KeyboardInputPreferences.read(context, false).frostedGlass)
        values["dark_mode"] = 1
        assertEquals(light, KeyboardInputPreferences.read(context, false).frostedGlass)
        assertEquals(dark, KeyboardInputPreferences.read(context).frostedGlass)
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

    @Test fun `cursor hold delay persists tenths normalizes invalid values and leaves other actions unchanged`() {
        for (tenth in 1..10) {
            val seconds = tenth / 10f
            KeyboardInputPreferences(cursorHoldSeconds = seconds).save(context)
            val restored = KeyboardInputPreferences.read(context)
            assertEquals(seconds, restored.cursorHoldSeconds, 0f)
            assertEquals(tenth * 100L, restored.spaceHoldDelayMs)
            for (action in listOf(SpaceHoldAction.REPEAT, SpaceHoldAction.VOICE_TOGGLE)) {
                assertEquals(300L, restored.copy(spaceHold = action, cursorGesture = CursorGestureMode.NONE).spaceHoldDelayMs)
                assertEquals("Space cursor override uses its configured delay", tenth * 100L,
                    restored.copy(spaceHold = action, cursorGesture = CursorGestureMode.SPACE).spaceHoldDelayMs)
            }
        }
        val invalid = listOf(
            Float.NaN to 0.2f,
            Float.POSITIVE_INFINITY to 0.2f,
            Float.NEGATIVE_INFINITY to 0.2f,
            -1f to 0.1f,
            9f to 1f,
            0.26f to 0.3f,
        )
        for ((stored, expected) in invalid) {
            values["cursor_hold_seconds"] = stored
            assertEquals("Read normalizes $stored", expected, KeyboardInputPreferences.read(context).cursorHoldSeconds, 0f)
            KeyboardInputPreferences(cursorHoldSeconds = stored).save(context)
            assertEquals("Save normalizes $stored", expected, values["cursor_hold_seconds"] as Float, 0f)
            assertEquals(expected, KeyboardInputPreferences.read(context).cursorHoldSeconds, 0f)
        }
    }

    @Test
    fun `legacy voice hold migrates to cursor`() {
        values["space_hold_action"] = "VOICE"
        assertEquals(SpaceHoldAction.CURSOR, KeyboardInputPreferences.read(context).spaceHold)
    }

    @Test fun `symbol input keeps swipe default and persists an explicit hold choice`() {
        assertEquals(SymbolInputMode.SWIPE_UP, KeyboardInputPreferences.read(context).symbolInputMode)
        val hold = KeyboardInputPreferences(symbolInputMode = SymbolInputMode.LONG_PRESS,
            spaceHold = SpaceHoldAction.VOICE_TOGGLE, cursorGesture = CursorGestureMode.KEYBOARD)
        hold.save(context)
        assertEquals(hold, KeyboardInputPreferences.read(context))
        hold.copy(symbolInputMode = SymbolInputMode.SWIPE_UP).save(context)
        assertEquals(SymbolInputMode.SWIPE_UP, KeyboardInputPreferences.read(context).symbolInputMode)
        values["symbol_input_mode"] = "unknown-future-mode"
        assertEquals(SymbolInputMode.SWIPE_UP, KeyboardInputPreferences.read(context).symbolInputMode)
        assertEquals(SpaceHoldAction.VOICE_TOGGLE, KeyboardInputPreferences.read(context).effectiveSpaceHold)
    }

    @Test fun `explicit new voice toggle survives save while cursor override preserves it`() {
        val voice = KeyboardInputPreferences(spaceHold = SpaceHoldAction.VOICE_TOGGLE, cursorGesture = CursorGestureMode.KEYBOARD)
        voice.save(context)
        assertEquals(SpaceHoldAction.VOICE_TOGGLE, KeyboardInputPreferences.read(context).effectiveSpaceHold)
        voice.copy(cursorGesture = CursorGestureMode.SPACE).save(context)
        assertEquals(SpaceHoldAction.CURSOR, KeyboardInputPreferences.read(context).effectiveSpaceHold)
        assertEquals(SpaceHoldAction.VOICE_TOGGLE, KeyboardInputPreferences.read(context).spaceHold)
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

    @Test fun `split keyboard is opt in and persists independently of input settings`() {
        assertEquals(false, KeyboardInputPreferences.read(context).splitKeyboardEnabled)
        SettingsPreferences.setSplitKeyboardEnabled(context, true)
        assertEquals(true, KeyboardInputPreferences.read(context).splitKeyboardEnabled)
        KeyboardInputPreferences().save(context)
        assertEquals(true, SettingsPreferences.isSplitKeyboardEnabled(context))
        SettingsPreferences.setSplitKeyboardEnabled(context, false)
        assertEquals(false, KeyboardInputPreferences.read(context).splitKeyboardEnabled)
    }

    @Test fun `legacy combined preference preserves both effects until edited independently`() {
        for (old in listOf(false, true)) {
            values.clear()
            values[KeyEffectPreferences.GLOW] = old
            assertEquals(old, KeyboardInputPreferences.read(context).keyAnimationEnabled)
            KeyEffectPreferences.setGlowEnabled(prefs, !old)
            assertEquals(old, KeyboardInputPreferences.read(context).keyAnimationEnabled)
            assertEquals(!old, KeyboardInputPreferences.read(context).keyGlowEnabled)
            KeyEffectPreferences.setGlowEnabled(prefs, old)
            assertEquals(old, KeyboardInputPreferences.read(context).keyAnimationEnabled)
        }
    }

    @Test fun `all four combinations persist without one switch changing the other`() {
        for (glow in listOf(false, true)) for (animation in listOf(false, true)) {
            values.clear()
            KeyEffectPreferences.setAnimationEnabled(prefs, animation)
            KeyEffectPreferences.setGlowEnabled(prefs, glow)
            val effects = KeyboardInputPreferences.read(context)
            assertEquals(glow, effects.keyGlowEnabled)
            assertEquals(animation, effects.keyAnimationEnabled)
            KeyboardInputPreferences().save(context)
            assertEquals(glow, KeyboardInputPreferences.read(context).keyGlowEnabled)
            assertEquals(animation, KeyboardInputPreferences.read(context).keyAnimationEnabled)
            KeyEffectPreferences.setAnimationEnabled(prefs, !animation)
            assertEquals(glow, KeyboardInputPreferences.read(context).keyGlowEnabled)
        }
        values.clear()
        KeyEffectPreferences.setGlowEnabled(prefs, true)
        assertEquals(false, KeyboardInputPreferences.read(context).keyAnimationEnabled)
    }

}
