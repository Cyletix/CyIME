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
        whenever(editor.putInt(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Int>(1); editor
        }
    }

    private fun legacy(config: FrostedGlassConfig) {
        values[FrostedGlassPreferences.KEY_BLUR_RADIUS] = config.blurRadiusDp
        values[FrostedGlassPreferences.KEY_BACKGROUND_OPACITY] = config.backgroundOpacity
        values[FrostedGlassPreferences.KEY_KEY_OPACITY] = config.keyOpacity
    }

    @Test fun `fresh appearances have swapped defaults without writing preferences`() {
        assertEquals(FrostedGlassConfig(false, 10f, 0.8f, 0.2f), FrostedGlassPreferences.read(context, true))
        assertEquals(FrostedGlassConfig(false, 10f, 0.2f, 0.8f), FrostedGlassPreferences.read(context, false))
        assertTrue(values.isEmpty())
    }

    @Test fun `opacity controls remain independent and appearance changes swap them both ways`() {
        val dark = FrostedGlassConfig(true, 12f, 0.61f, 0.29f)
        SettingsPreferences.setDarkMode(context, 1)
        FrostedGlassPreferences.save(context, dark)
        FrostedGlassPreferences.save(context, dark.copy(backgroundOpacity = 0.47f))
        assertEquals(0.29f, FrostedGlassPreferences.read(context).keyOpacity)
        SettingsPreferences.setDarkMode(context, 0)
        assertEquals(0.29f, FrostedGlassPreferences.read(context).backgroundOpacity)
        assertEquals(0.47f, FrostedGlassPreferences.read(context).keyOpacity)
        val light = FrostedGlassPreferences.read(context)
        FrostedGlassPreferences.save(context, light.copy(keyOpacity = 0.76f))
        assertEquals(0.29f, FrostedGlassPreferences.read(context).backgroundOpacity)
        SettingsPreferences.setDarkMode(context, 1)
        assertEquals(dark.copy(backgroundOpacity = 0.76f), FrostedGlassPreferences.read(context))
    }

    @Test fun `conflicting old appearance pairs migrate from active appearance without deleting either record`() {
        for (activeDark in listOf(false, true)) {
            values.clear()
            SettingsPreferences.setDarkMode(context, if (activeDark) 1 else 0)
            val old = mapOf(
                FrostedGlassPreferences.KEY_BACKGROUND_OPACITY + "_dark" to 0.67f,
                FrostedGlassPreferences.KEY_KEY_OPACITY + "_dark" to 0.23f,
                FrostedGlassPreferences.KEY_BACKGROUND_OPACITY + "_light" to 0.34f,
                FrostedGlassPreferences.KEY_KEY_OPACITY + "_light" to 0.81f,
            )
            values.putAll(old)
            val expected = if (activeDark) 0.67f to 0.23f else 0.81f to 0.34f
            assertEquals(expected.first, FrostedGlassPreferences.read(context, true).backgroundOpacity)
            assertEquals(expected.second, FrostedGlassPreferences.read(context, true).keyOpacity)
            old.forEach { (key, value) -> assertEquals(value, values[key]) }
            SettingsPreferences.setDarkMode(context, if (activeDark) 0 else 1)
            assertEquals(expected.first, FrostedGlassPreferences.read(context, true).backgroundOpacity)
            assertEquals(expected.second, FrostedGlassPreferences.read(context, true).keyOpacity)
        }
    }

    @Test fun `explicit rendered appearance overrides global light mode`() {
        values["dark_mode"] = 0
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context))
        assertEquals(FrostedGlassConfig.defaults(true), FrostedGlassPreferences.read(context, true))
        values["dark_mode"] = 1
        assertEquals(FrostedGlassConfig.defaults(false), FrostedGlassPreferences.read(context, false))
    }

    @Test fun `saving either appearance swaps opacity roles and preserves each blur`() {
        val dark = FrostedGlassConfig(true, 17f, 0.36f, 0.72f)
        val light = FrostedGlassConfig(true, 13f, 0.27f, 0.83f)
        FrostedGlassPreferences.save(context, dark, true)
        FrostedGlassPreferences.save(context, light, false)
        assertEquals(FrostedGlassProfiles(light, dark.copy(backgroundOpacity = 0.83f, keyOpacity = 0.27f)),
            FrostedGlassPreferences.readProfiles(context))
        FrostedGlassPreferences.save(context, dark.copy(enabled = false), true)
        assertEquals(FrostedGlassProfiles(light.copy(enabled = false, backgroundOpacity = 0.72f, keyOpacity = 0.36f),
            dark.copy(enabled = false)),
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
        assertEquals(FrostedGlassConfig.defaults(false).copy(backgroundOpacity = 0.34f, keyOpacity = 0.63f),
            FrostedGlassPreferences.read(context, false))
        values["dark_mode"] = 0
        assertEquals(custom, FrostedGlassPreferences.read(context, true))
        assertEquals(FrostedGlassConfig.defaults(false).copy(backgroundOpacity = 0.34f, keyOpacity = 0.63f),
            FrostedGlassPreferences.read(context))
        assertEquals(19f, values[FrostedGlassPreferences.KEY_BLUR_RADIUS])
    }

    @Test fun `legacy light customization stays light when appearance changes`() {
        values["dark_mode"] = 0
        val custom = FrostedGlassConfig(false, 16f, 0.31f, 0.87f)
        legacy(custom)
        assertEquals(custom, FrostedGlassPreferences.read(context, false))
        values["dark_mode"] = 1
        assertEquals(custom, FrostedGlassPreferences.read(context, false))
        assertEquals(FrostedGlassConfig.defaults(true).copy(backgroundOpacity = 0.87f, keyOpacity = 0.31f),
            FrostedGlassPreferences.read(context, true))
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
        assertEquals(FrostedGlassConfig(true, 10f, 1f, 0.8f), FrostedGlassPreferences.read(context, false))
        assertEquals(FrostedGlassConfig(true, 40f, 0.8f, 1f), FrostedGlassPreferences.read(context, true))
        assertEquals(true, values["key_glow_enabled"])
        assertEquals(0.8f, values["keyboard_alpha"])
    }

    @Test fun `theme selection preserves material switch and both appearances`() {
        val profiles = FrostedGlassProfiles(
            FrostedGlassConfig(true, 13f, 0.3f, 0.6f), FrostedGlassConfig(true, 17f, 0.6f, 0.3f))
        FrostedGlassPreferences.saveProfiles(context, profiles)
        for (palette in listOf("pure_black", "lavender_purple", "soft_blue", "transparent_glass", "custom_user_theme")) {
            SettingsPreferences.setKeyboardTheme(context, palette)
            assertEquals(palette, SettingsPreferences.getKeyboardTheme(context))
            assertEquals(profiles, FrostedGlassPreferences.readProfiles(context))
        }
    }

    @Test fun `changing colour never enables a disabled material`() {
        val profiles = FrostedGlassProfiles(FrostedGlassConfig.defaults(false), FrostedGlassConfig.defaults(true))
        FrostedGlassPreferences.saveProfiles(context, profiles)
        SettingsPreferences.setKeyboardTheme(context, "soft_blue")
        assertEquals(profiles, FrostedGlassPreferences.readProfiles(context))
        assertEquals("soft_blue", SettingsPreferences.getKeyboardTheme(context))
    }

    @Test fun `enabling and disabling glass never replaces the selected colour`() {
        SettingsPreferences.setKeyboardTheme(context, "lavender_purple")
        val customized = FrostedGlassProfiles(
            FrostedGlassConfig(true, 19f, 0.2f, 0.8f), FrostedGlassConfig(true, 15f, 0.8f, 0.2f))
        for (enabled in listOf(true, false, true)) {
            FrostedGlassPreferences.saveProfiles(context, customized.withEnabled(enabled))
            assertEquals("lavender_purple", SettingsPreferences.getKeyboardTheme(context))
            assertEquals(customized.withEnabled(enabled), FrostedGlassPreferences.readProfiles(context))
        }
    }

    @Test fun `reading fixed gradient theme preserves enabled state opacity and blur without migration`() {
        for (enabled in listOf(false, true)) {
            val profiles = FrostedGlassProfiles(
                FrostedGlassConfig(enabled, 12f, 0.3f, 0.6f), FrostedGlassConfig(enabled, 17f, 0.6f, 0.3f))
            FrostedGlassPreferences.saveProfiles(context, profiles)
            values["keyboard_theme"] = "transparent_glass"
            val before = values.toMap()
            assertEquals("transparent_glass", SettingsPreferences.getKeyboardTheme(context))
            assertEquals(profiles, FrostedGlassPreferences.readProfiles(context))
            assertEquals(before, values)
            assertFalse(values.containsKey("glass_palette_split_migrated"))
        }
    }

    @Test fun `selecting fixed gradient and another colour preserves all material parameters`() {
        for (enabled in listOf(false, true)) {
            val profiles = FrostedGlassProfiles(
                FrostedGlassConfig(enabled, 7f, 0.31f, 0.68f), FrostedGlassConfig(enabled, 23f, 0.68f, 0.31f))
            FrostedGlassPreferences.saveProfiles(context, profiles)
            val parameters = values.filterKeys { it != "keyboard_theme" }
            for (theme in listOf("transparent_glass", "soft_blue", "transparent_glass")) {
                SettingsPreferences.setKeyboardTheme(context, theme)
                assertEquals(theme, SettingsPreferences.getKeyboardTheme(context))
                assertEquals(profiles, FrostedGlassPreferences.readProfiles(context))
                assertEquals(parameters, values.filterKeys { it != "keyboard_theme" })
            }
        }
    }

    @Test fun `existing legacy colour migration preserves independent material switch`() {
        values["keyboard_theme"] = "858AdvanceColor"
        values[FrostedGlassPreferences.KEY_ENABLED] = true
        SettingsPreferences.setKeyboardTheme(context, "lavender_purple")
        assertEquals("lavender_purple", SettingsPreferences.getKeyboardTheme(context))
        assertTrue(FrostedGlassPreferences.read(context, true).enabled)
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
