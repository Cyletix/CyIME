package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
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
        whenever(prefs.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
        whenever(prefs.getBoolean(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
        }
        whenever(prefs.getInt(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Int ?: it.getArgument<Int>(1)
        }
        whenever(prefs.getString(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1)
        }
        whenever(prefs.getFloat(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] as? Float ?: it.getArgument<Float>(1)
        }
        whenever(editor.putString(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<String>(1); editor
        }
        whenever(editor.putBoolean(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Boolean>(1); editor
        }
        whenever(editor.putFloat(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Float>(1); editor
        }
    }

    private fun legacy(config: FrostedGlassConfig) {
        values[FrostedGlassPreferences.KEY_BLUR_RADIUS] = config.blurRadiusDp
        values[FrostedGlassPreferences.KEY_BACKGROUND_OPACITY] = config.backgroundOpacity
        values[FrostedGlassPreferences.KEY_KEY_OPACITY] = config.keyOpacity
    }

    @Test fun `fresh appearances have independent requested defaults`() {
        assertEquals(FrostedGlassConfig(false, 10f, 0.8f, 0.2f), FrostedGlassPreferences.read(context, true))
        assertEquals(FrostedGlassConfig(false, 10f, 0.2f, 0.8f), FrostedGlassPreferences.read(context, false))
        assertTrue(values.isEmpty())
    }

    @Test fun `explicit rendered appearance overrides global light mode`() {
        values["dark_mode"] = 0
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context))
        assertEquals(FrostedGlassConfig.defaults(true), FrostedGlassPreferences.read(context, true))
        values["dark_mode"] = 1
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context, false))
    }

    @Test fun `saving one appearance preserves the other customization`() {
        val dark = FrostedGlassConfig(true, 17f, 0.36f, 0.72f)
        val light = FrostedGlassConfig(true, 13f, 0.27f, 0.83f)
        FrostedGlassPreferences.save(context, dark, true)
        FrostedGlassPreferences.save(context, light, false)
        assertEquals(FrostedGlassProfiles(light, dark), FrostedGlassPreferences.readProfiles(context))
        FrostedGlassPreferences.save(context, dark.copy(enabled = false), true)
        assertEquals(FrostedGlassProfiles(light.copy(enabled = false), dark.copy(enabled = false)),
            FrostedGlassPreferences.readProfiles(context))
    }

    @Test fun `legacy dark preset does not contaminate light mode`() {
        values["dark_mode"] = 0
        legacy(FrostedGlassConfig.defaults(true))
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context, false))
        assertEquals(FrostedGlassConfig.defaults(true), FrostedGlassPreferences.read(context, true))
        assertEquals(0.8f, values[FrostedGlassPreferences.KEY_BACKGROUND_OPACITY])
    }

    @Test fun `legacy dark custom values migrate once without losing original keys`() {
        val custom = FrostedGlassConfig(false, 19f, 0.63f, 0.34f)
        values["dark_mode"] = 1
        legacy(custom)
        assertEquals(custom, FrostedGlassPreferences.read(context, true))
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context, false))
        values["dark_mode"] = 0
        assertEquals(custom, FrostedGlassPreferences.read(context, true))
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context))
        assertEquals(19f, values[FrostedGlassPreferences.KEY_BLUR_RADIUS])
    }

    @Test fun `legacy light customization stays light when appearance changes`() {
        values["dark_mode"] = 0
        val custom = FrostedGlassConfig(false, 16f, 0.31f, 0.87f)
        legacy(custom)
        assertEquals(custom, FrostedGlassPreferences.read(context, false))
        values["dark_mode"] = 1
        assertEquals(custom, FrostedGlassPreferences.read(context, false))
        assertEquals(FrostedGlassConfig.defaults(true), FrostedGlassPreferences.read(context, true))
    }

    @Test fun `existing appearance settings win over legacy fallback`() {
        legacy(FrostedGlassConfig(false, 19f, 0.63f, 0.34f))
        values[FrostedGlassPreferences.KEY_BLUR_RADIUS + "_dark"] = 7f
        values[FrostedGlassPreferences.KEY_BACKGROUND_OPACITY + "_dark"] = 0.7f
        values[FrostedGlassPreferences.KEY_KEY_OPACITY + "_dark"] = 0.25f
        assertEquals(FrostedGlassConfig(false, 7f, 0.7f, 0.25f), FrostedGlassPreferences.read(context, true))
    }

    @Test fun `normalization uses the matching appearance defaults and leaves unrelated settings alone`() {
        values["key_glow_enabled"] = true
        values["keyboard_alpha"] = 0.8f
        FrostedGlassPreferences.saveProfiles(context, FrostedGlassProfiles(
            light = FrostedGlassConfig(true, Float.NaN, Float.POSITIVE_INFINITY, -2f),
            dark = FrostedGlassConfig(true, 50f, Float.NaN, 8f),
        ))
        assertEquals(FrostedGlassConfig(true, 10f, 0.2f, 0f), FrostedGlassPreferences.read(context, false))
        assertEquals(FrostedGlassConfig(true, 40f, 0.8f, 1f), FrostedGlassPreferences.read(context, true))
        assertEquals(true, values["key_glow_enabled"])
        assertEquals(0.8f, values["keyboard_alpha"])
    }

    @Test fun `theme selection toggles effect without erasing either appearance`() {
        FrostedGlassPreferences.save(context, FrostedGlassConfig(true, 13f, 0.6f, 0.3f), true)
        SettingsPreferences.setKeyboardTheme(context, "pure_black")
        assertFalse(FrostedGlassPreferences.read(context, true).enabled)
        assertEquals(13f, FrostedGlassPreferences.read(context, true).blurRadiusDp)
        SettingsPreferences.setKeyboardTheme(context, "transparent_glass")
        assertTrue(FrostedGlassPreferences.read(context, false).enabled)
        assertEquals(FrostedGlassConfig.defaults(false, true), FrostedGlassPreferences.read(context, false))
    }

    @Test fun `all appearance and display keys participate in preference updates`() {
        for (suffix in listOf("_dark", "_light")) {
            assertTrue(FrostedGlassPreferences.keys.contains(FrostedGlassPreferences.KEY_BLUR_RADIUS + suffix))
            assertTrue(FrostedGlassPreferences.keys.contains(FrostedGlassPreferences.KEY_BACKGROUND_OPACITY + suffix))
            assertTrue(FrostedGlassPreferences.keys.contains(FrostedGlassPreferences.KEY_KEY_OPACITY + suffix))
        }
        assertTrue(FrostedGlassPreferences.keys.contains("dark_mode"))
    }
}
