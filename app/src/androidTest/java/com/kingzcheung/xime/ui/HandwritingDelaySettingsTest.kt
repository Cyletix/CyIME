package com.kingzcheung.xime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.settings.InputExperienceSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HandwritingDelaySettingsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun visibleSettingDefaultsToHalfSecondAndPersistsTenths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val keys = listOf("handwriting_pause_seconds", "handwriting_pause_seconds_v2")
        val saved = keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().remove(keys[0]).remove(keys[1]).commit()
            rule.setContent { MaterialTheme { InputExperienceSettings() } }
            rule.onNodeWithText("手写停顿识别：0.5 秒").assertIsDisplayed()
            rule.onNodeWithTag("handwriting-pause-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
            rule.onNodeWithText("手写停顿识别：0.7 秒").assertIsDisplayed()
            rule.runOnIdle { assertEquals(0.7f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f) }
            rule.onNodeWithTag("handwriting-pause-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
            rule.onNodeWithText("手写停顿识别：0.1 秒").assertIsDisplayed()
            rule.runOnIdle { assertEquals(0.1f, KeyboardInputPreferences.read(context).handwritingPauseSeconds, 0f) }
        } finally {
            prefs.edit().also { edit -> saved.forEach { (key, value) -> if (value is Float) edit.putFloat(key, value) else edit.remove(key) } }.commit()
        }
    }
}
