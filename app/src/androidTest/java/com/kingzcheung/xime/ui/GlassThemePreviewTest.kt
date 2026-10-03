package com.kingzcheung.xime.ui

import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.SharedPreferencesTestRule
import com.kingzcheung.xime.settings.FrostedGlassProfiles
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.ThemePreviewSheet
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.TransparentGlassTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GlassThemePreviewTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val preferences = SharedPreferencesTestRule()
    private val context by lazy {
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences.getSharedPreferences()
        }
    }

    @Test fun draftCancelsWithoutSavingAndApplyPublishesOnlyChosenTheme() {
        SettingsPreferences.setKeyboardTheme(context, "pure_black")
        var showing by mutableStateOf(true)
        var applied: FrostedGlassProfiles? = null
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    if (showing) ThemePreviewSheet(TransparentGlassTheme.create(), onApply = {
                        applied = it
                        FrostedGlassPreferences.saveProfiles(context, it)
                        showing = false
                    }, onDismiss = { showing = false })
                }
            }
        }
        fun changeSlider(label: String, value: Float) {
            rule.onNodeWithContentDescription(label).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        fun selectAppearance(isDark: Boolean) {
            rule.onNodeWithTag(if (isDark) "theme-preview-dark" else "theme-preview-light")
                .performScrollTo().performClick()
            rule.waitForIdle()
        }
        val defaults = FrostedGlassPreferences.readProfiles(context)
        changeSlider("模糊强度", 11f)
        selectAppearance(true)
        rule.onNodeWithText("10 dp").performScrollTo().assertIsDisplayed()
        changeSlider("模糊强度", 13f)
        rule.runOnIdle {
            assertEquals("pure_black", SettingsPreferences.getKeyboardTheme(context))
            assertEquals(defaults, FrostedGlassPreferences.readProfiles(context))
        }
        rule.onNodeWithText("取消").performScrollTo().performClick()
        rule.runOnIdle { assertNull(applied); showing = true }
        rule.onNodeWithText("10 dp").performScrollTo().assertIsDisplayed()
        changeSlider("模糊强度", 19f)
        changeSlider("背景遮罩不透明度", 0.31f)
        changeSlider("按键不透明度", 0.71f)
        selectAppearance(true)
        rule.onNodeWithText("10 dp").performScrollTo().assertIsDisplayed()
        changeSlider("模糊强度", 17f)
        changeSlider("背景遮罩不透明度", 0.73f)
        changeSlider("按键不透明度", 0.23f)
        selectAppearance(false)
        rule.onNodeWithText("19 dp").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("按键不透明度").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("应用").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(19f, applied!!.light.blurRadiusDp)
            assertEquals(0.31f, applied!!.light.backgroundOpacity)
            assertEquals(0.71f, applied!!.light.keyOpacity)
            assertEquals(17f, applied!!.dark.blurRadiusDp)
            assertEquals(0.73f, applied!!.dark.backgroundOpacity)
            assertEquals(0.23f, applied!!.dark.keyOpacity)
            assertEquals(applied, FrostedGlassPreferences.readProfiles(context))
            assertEquals("transparent_glass", SettingsPreferences.getKeyboardTheme(context))
            SettingsPreferences.setKeyboardTheme(context, "lavender_purple")
            assertFalse(FrostedGlassPreferences.read(context, isDark = false).enabled)
            assertFalse(FrostedGlassPreferences.read(context, isDark = true).enabled)
            assertEquals(19f, FrostedGlassPreferences.read(context, isDark = false).blurRadiusDp)
            assertEquals(17f, FrostedGlassPreferences.read(context, isDark = true).blurRadiusDp)
        }
    }

    @Test fun catalogueStartsWithGlassBlackWhiteLavenderThenBlue() {
        assertEquals(listOf("transparent_glass", "pure_black", "lavender_purple", "soft_blue"),
            KeyboardThemes.themes.take(4).map { it.id })
    }
}
