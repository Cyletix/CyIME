package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FrostedGlassPreferencesTest {
    private val context = mock<Context>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getBoolean(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
        }
        whenever(prefs.getString(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1)
        }
        whenever(editor.putString(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<String>(1)
            editor
        }
        whenever(prefs.getFloat(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Float ?: it.getArgument<Float>(1)
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
    fun `existing installations stay unchanged until enabled`() {
        assertEquals(FrostedGlassConfig(), FrostedGlassPreferences.read(context))
    }

    @Test
    fun `all controls persist and disabling retains the chosen effect`() {
        val selected = FrostedGlassConfig(true, 17f, 0.36f, 0.72f)
        FrostedGlassPreferences.save(context, selected)
        assertEquals(selected, FrostedGlassPreferences.read(context))
        FrostedGlassPreferences.save(context, selected.copy(enabled = false))
        assertEquals(selected.copy(enabled = false), FrostedGlassPreferences.read(context))
        assertEquals(FrostedGlassPreferences.keys, values.keys)
    }

    @Test
    fun `out of range stored numbers are bounded and nonfinite numbers use defaults`() {
        for ((stored, expected) in listOf(
            FrostedGlassConfig(false, -4f, 2f, -0.2f) to
                FrostedGlassConfig(blurRadiusDp = 0f, backgroundOpacity = 1f, keyOpacity = 0f),
            FrostedGlassConfig(false, 80f, Float.NaN, Float.POSITIVE_INFINITY) to
                FrostedGlassConfig(blurRadiusDp = 40f),
            FrostedGlassConfig(false, Float.NEGATIVE_INFINITY, -3f, 2f) to
                FrostedGlassConfig(backgroundOpacity = 0f, keyOpacity = 1f),
        )) {
            values[FrostedGlassPreferences.KEY_BLUR_RADIUS] = stored.blurRadiusDp
            values[FrostedGlassPreferences.KEY_BACKGROUND_OPACITY] = stored.backgroundOpacity
            values[FrostedGlassPreferences.KEY_KEY_OPACITY] = stored.keyOpacity
            assertEquals(expected, FrostedGlassPreferences.read(context))
        }
    }

    @Test
    fun `save sanitizes numbers and preserves other effects`() {
        values["key_glow_enabled"] = true
        values["keyboard_alpha"] = 0.8f
        FrostedGlassPreferences.save(
            context,
            FrostedGlassConfig(true, Float.NaN, Float.NEGATIVE_INFINITY, 5f),
        )
        assertEquals(FrostedGlassConfig(true, 24f, 0.55f, 1f), FrostedGlassPreferences.read(context))
        assertEquals(true, values["key_glow_enabled"])
        assertEquals(0.8f, values["keyboard_alpha"])
    }
    @Test
    fun `legacy effect migrates once and choosing another theme disables it without erasing parameters`() {
        values["keyboard_theme"] = "lavender_purple"
        values[FrostedGlassPreferences.KEY_ENABLED] = true
        values[FrostedGlassPreferences.KEY_BLUR_RADIUS] = 13f
        assertEquals("transparent_glass", SettingsPreferences.getKeyboardTheme(context))
        assertEquals(true, FrostedGlassPreferences.read(context).enabled)
        SettingsPreferences.setKeyboardTheme(context, "pure_black")
        assertEquals("pure_black", SettingsPreferences.getKeyboardTheme(context))
        assertEquals(false, FrostedGlassPreferences.read(context).enabled)
        assertEquals(13f, FrostedGlassPreferences.read(context).blurRadiusDp)
        SettingsPreferences.setKeyboardTheme(context, "transparent_glass")
        assertEquals(true, FrostedGlassPreferences.read(context).enabled)
        assertEquals(13f, FrostedGlassPreferences.read(context).blurRadiusDp)
    }

}
