package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.settings.VisualStylePicker
import com.kingzcheung.xime.ui.theme.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualStyleIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preservingAppearance(block: () -> Unit) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.getString(SettingsPreferences.KEY_VISUAL_STYLE, null)
        try { block() } finally {
            rule.runOnIdle {
                prefs.edit().also { if (saved == null) it.remove(SettingsPreferences.KEY_VISUAL_STYLE)
                    else it.putString(SettingsPreferences.KEY_VISUAL_STYLE, saved) }.commit()
                VisualStyles.current = VisualStyle.fromId(saved)
            }
        }
    }

    @Test fun pickerPersistsEachPresetAndRestoresOriginalThemeUnchanged() = preservingAppearance {
        val originalId = SettingsPreferences.getKeyboardTheme(context)
        val originalDark = SettingsPreferences.getDarkMode(context)
        val originalTheme = KeyboardThemes.getThemeById(originalId)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                XimeTheme { Box(Modifier.size(460.dp, 700.dp)) {
                    VisualStylePicker(VisualStyles.current) { SettingsPreferences.setVisualStyle(context, it) }
                } }
            }
        }
        VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.forEach { style ->
            rule.onNodeWithTag("visual-style-${style.id}").performClick().assertIsSelected()
            assertEquals(style, SettingsPreferences.getVisualStyle(context))
            assertEquals(originalId, SettingsPreferences.getKeyboardTheme(context))
            assertEquals(originalDark, SettingsPreferences.getDarkMode(context))
            assertEquals(originalTheme, KeyboardThemes.getThemeById(originalId))
            rule.runOnIdle {
                VisualStyles.current = VisualStyle.ORIGINAL
                KeyboardThemes.reload(context)
            }
            assertEquals(style, VisualStyles.current)
        }
        rule.onNodeWithTag("visual-style-original").performClick()
        assertEquals(VisualStyle.ORIGINAL, SettingsPreferences.getVisualStyle(context))
        assertEquals(originalId, KeyboardThemes.getRenderingScheme(originalId).id)
    }

    @Test fun actualKeyboardSwitchesAllMaterialsWithoutChangingKeyActions() = preservingAppearance {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        KeysConfigHelper.loadConfig(app)
        val vm = KeyboardViewModel(app)
        vm.setKeyboardState(KeyboardLayoutState.Chinese)
        val keys = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                XimeTheme {
                    KeyboardView(vm, KeyboardUiState(currentSchemaId = "rime_ice", themeId = "soft_blue",
                        isDarkTheme = VisualStyles.current.dark ?: true),
                        KeyboardCallbacks(onKeyPress = { key, _ -> keys += key }, onCandidateSelect = {}),
                        modifier = Modifier.size(420.dp, 320.dp).testTag("styled-keyboard"))
                }
            }
        }
        VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.forEach { style ->
            rule.runOnIdle { SettingsPreferences.setVisualStyle(context, style); keys.clear() }
            rule.onNodeWithTag("qwerty-key:a").performTouchInput { click() }
            rule.onNodeWithTag("qwerty-delete-key").performTouchInput { click() }
            rule.onNodeWithTag("qwerty-enter-key").performTouchInput { click() }
            rule.runOnIdle { assertEquals(listOf("a", "delete", "enter"), keys) }
            val cap = rule.onNodeWithTag("qwerty-delete-key").captureToImage().toPixelMap()
            val actual = cap[(cap.width * .2f).toInt(), cap.height / 2]
            val expected = requireNotNull(VisualStyles.palette(style)).specialKeyLight
            assertTrue("${style.id}: function key must use preset, not the saved blue theme",
                kotlin.math.abs(actual.red - expected.red) < .11f &&
                kotlin.math.abs(actual.green - expected.green) < .11f &&
                kotlin.math.abs(actual.blue - expected.blue) < .11f)
            File(context.getExternalFilesDir(null), "visual-${style.id}.png").outputStream().use {
                rule.onNodeWithTag("styled-keyboard").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
