package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BackgroundMotionPreferencesTest {
    private val context = mock<Context>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Any>()

    @Before fun setUp() {
        whenever(context.getSharedPreferences(any<String>(), any<Int>())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getBoolean(any<String>(), any<Boolean>())).thenAnswer {
            values[it.getArgument<String>(0)] as Boolean? ?: it.getArgument<Boolean>(1)
        }
        whenever(prefs.getString(any<String>(), anyOrNull<String>())).thenAnswer {
            values[it.getArgument<String>(0)] as String? ?: it.getArgument<String?>(1)
        }
        whenever(editor.putBoolean(any<String>(), any<Boolean>())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Boolean>(1)
            editor
        }
        whenever(editor.putString(any<String>(), anyOrNull<String>())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<String>(1)
            editor
        }
    }

    @Test fun offByDefault() {
        assertEquals(BackgroundMotionSettings(enabled = false, effect = BackgroundMotionEffect.CYCLE,
            speed = BackgroundMotionSpeed.NORMAL), BackgroundMotionPreferences.read(context))
        assertEquals("读的时候不写", emptyMap<String, Any>(), values)
    }

    @Test fun choicesPersist() {
        BackgroundMotionPreferences.setEnabled(context, true)
        BackgroundMotionPreferences.setEffect(context, BackgroundMotionEffect.PAN)
        BackgroundMotionPreferences.setSpeed(context, BackgroundMotionSpeed.SLOW)
        assertEquals(BackgroundMotionSettings(true, BackgroundMotionEffect.PAN, BackgroundMotionSpeed.SLOW),
            BackgroundMotionPreferences.read(context))
    }

    @Test fun slidesKeepOrderDropBlanksAndCapAtEight() {
        val many = (1..12).map { "themes/custom-keyboard-bg-slide-$it.jpg" }
        val round = BackgroundMotionPreferences.decodeSlides(BackgroundMotionPreferences.encodeSlides(listOf("", "  ") + many))
        assertEquals(many.take(BackgroundMotionPreferences.MAX_SLIDES), round)
        assertEquals(emptyList<String>(), BackgroundMotionPreferences.decodeSlides(null))
        assertEquals(listOf("themes/a.jpg"), BackgroundMotionPreferences.decodeSlides("themes/a.jpg\nthemes/a.jpg"))
    }

    @Test fun unknownStoredValuesFallBack() {
        values[BackgroundMotionPreferences.KEY_EFFECT] = "spin"
        values[BackgroundMotionPreferences.KEY_SPEED] = "warp"
        val read = BackgroundMotionPreferences.read(context)
        assertEquals(BackgroundMotionEffect.CYCLE, read.effect)
        assertEquals(BackgroundMotionSpeed.NORMAL, read.speed)
    }
}
