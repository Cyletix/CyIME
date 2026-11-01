package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PressBubbleToggleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun lettersFollowTheSwitchWithoutReopeningTheKeyboard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        SettingsPreferences.setShowPressBubble(app, false)
        val vm = KeyboardViewModel(app)
        val typed = StringBuilder()
        var observedSetting = false
        rule.setContent {
            val settings = rememberKeyboardInputPreferences()
            SideEffect { observedSetting = settings.showPressBubble }
            CompositionLocalProvider(LocalKeyboardInputPreferences provides settings) {
                KeyboardLayout({ typed.append(it) }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                    KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = true), true,
                    Modifier.requiredSize(350.dp, 228.dp))
            }
        }
        for (enabled in listOf(false, true, false, true, false)) {
            rule.runOnIdle { SettingsPreferences.setShowPressBubble(app, enabled) }
            rule.runOnIdle { assertEquals("Active keyboard preference", enabled, observedSetting) }
            for (letter in listOf("u", "b")) {
                val key = rule.onNodeWithTag("qwerty-key:$letter")
                key.performTouchInput { down(center) }
                rule.waitForIdle()
                val bubble = rule.onNodeWithTag("key-choice-popup", useUnmergedTree = true)
                if (enabled) bubble.assertExists("No tap bubble over $letter after enabling")
                else bubble.assertDoesNotExist()
                key.performTouchInput { up() }
                rule.mainClock.advanceTimeBy(100)
                bubble.assertDoesNotExist()
            }
            rule.onNodeWithTag("qwerty-key:b").performTouchInput {
                down(0, center)
                advanceEventTime(20)
                down(1, center)
                advanceEventTime(20)
                up(0)
                advanceEventTime(20)
                up(1)
            }
            rule.mainClock.advanceTimeBy(100)
        }
        assertEquals("ubbb".repeat(5), typed.toString())
    }
}
