package com.kingzcheung.xime.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.HardwareKeyboardPreferences
import com.kingzcheung.xime.settings.LanguageSwitchMode
import com.kingzcheung.xime.settings.LanguageSwitchPreferences
import com.kingzcheung.xime.ui.settings.HardwareKeyboardSettingsContent
import com.kingzcheung.xime.ui.settings.LanguageSettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageAndPagingSettingsTest {
    @get:Rule val rule = createComposeRule()

    private fun isolatedContext(): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val namespace = "language-paging-settings-${System.nanoTime()}"
        return object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("$namespace-$name", mode)
        }
    }

    @Test fun commonLanguagePolicyIsEditableOutsideHardwareSettingsAndKeepsShortcuts() {
        val context = isolatedContext()
        val hardwareBefore = HardwareKeyboardPreferences.read(context)
        val hardwarePage = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        if (hardwarePage.value) HardwareKeyboardSettingsContent {}
                        else LanguageSettingsContent {}
                    }
                }
            }
        }
        rule.onNodeWithTag("language-settings-list").performScrollToKey("language-key-switch")
        rule.onNodeWithTag("language-switch-mode:cycle").performScrollTo().performClick().assertIsSelected()
        assertEquals(LanguageSwitchMode.CYCLE, LanguageSwitchPreferences.read(context).mode)
        assertEquals(hardwareBefore, HardwareKeyboardPreferences.read(context))
        rule.runOnIdle { hardwarePage.value = true }
        rule.onNodeWithTag("language-key-switch-settings").assertDoesNotExist()
        rule.runOnIdle { hardwarePage.value = false }
        rule.onNodeWithTag("language-settings-list").performScrollToKey("language-key-switch")
        rule.onNodeWithTag("language-switch-mode:cycle").performScrollTo().assertIsSelected()
    }

    @Test fun candidatePageCheckboxesAreIndependentAndRememberSelections() {
        val context = isolatedContext()
        val visible = mutableStateOf(true)
        val languageBefore = LanguageSwitchPreferences.read(context)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        if (visible.value) HardwareKeyboardSettingsContent {}
                    }
                }
            }
        }
        val minus = "hardware-page_minus_equals"
        val brackets = "hardware-page_brackets"
        val punctuation = "hardware-page_comma_period"
        for (tag in listOf(minus, brackets, punctuation)) {
            rule.onNodeWithTag(tag).performScrollTo().assertIsOn()
        }
        rule.onNodeWithTag(brackets).performScrollTo().performClick().assertIsOff()
        rule.onNodeWithTag(minus).performScrollTo().assertIsOn()
        rule.onNodeWithTag(punctuation).performScrollTo().assertIsOn()
        rule.onNodeWithTag(punctuation).performClick().assertIsOff()
        assertEquals(languageBefore, LanguageSwitchPreferences.read(context))
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithTag(minus).performScrollTo().assertIsOn()
        rule.onNodeWithTag(brackets).performScrollTo().assertIsOff()
        rule.onNodeWithTag(punctuation).performScrollTo().assertIsOff()
        rule.onNodeWithTag(brackets).performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag(punctuation).performScrollTo().assertIsOff()
    }
}
