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
import com.kingzcheung.xime.settings.FrostedGlassConfig
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
        var applied: FrostedGlassConfig? = null
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    if (showing) ThemePreviewSheet(TransparentGlassTheme.create(), onApply = {
                        applied = it
                        FrostedGlassPreferences.save(context, it)
                        showing = false
                    }, onDismiss = { showing = false })
                }
            }
        }
        fun changeBlur(value: Float) {
            rule.onNodeWithContentDescription("模糊强度").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        changeBlur(11f)
        rule.runOnIdle {
            assertEquals("pure_black", SettingsPreferences.getKeyboardTheme(context))
            assertEquals(24f, FrostedGlassPreferences.read(context).blurRadiusDp)
        }
        rule.onNodeWithText("取消").performScrollTo().performClick()
        rule.runOnIdle { assertNull(applied); showing = true }
        rule.onNodeWithText("24 dp").performScrollTo().assertIsDisplayed()
        changeBlur(19f)
        rule.onNodeWithContentDescription("按键不透明度").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("应用").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(19f, applied!!.blurRadiusDp)
            assertEquals("transparent_glass", SettingsPreferences.getKeyboardTheme(context))
            SettingsPreferences.setKeyboardTheme(context, "lavender_purple")
            assertFalse(FrostedGlassPreferences.read(context).enabled)
            assertEquals(19f, FrostedGlassPreferences.read(context).blurRadiusDp)
        }
    }

    @Test fun catalogueStartsWithGlassBlackWhiteLavenderThenBlue() {
        assertEquals(listOf("transparent_glass", "pure_black", "lavender_purple", "soft_blue"),
            KeyboardThemes.themes.take(4).map { it.id })
    }
}
