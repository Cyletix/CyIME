package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardCursorChoiceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bodySwipeIsOptInAndDoesNotTypeWhenItMovesCursor() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val prefs = SettingsPreferences.getPrefsPublic(app)
        val old = prefs.getString("cursor_gesture_mode", null)
        val vm = KeyboardViewModel(app)
        vm.setKeyboardState(KeyboardLayoutState.Chinese)
        val moves = mutableListOf<Int>()
        val letters = mutableListOf<String>()
        try {
            prefs.edit().putString("cursor_gesture_mode", "SPACE").commit()
            rule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
                    KeyboardView(vm, KeyboardUiState(currentSchemaId = "rime_ice"),
                        KeyboardCallbacks(onKeyPress = { key, _ -> letters += key }, onCandidateSelect = {}, onCursorMove = { moves += it }),
                        Modifier.size(360.dp, 300.dp))
                } }
            }
            fun swipe() = rule.onNodeWithTag("qwerty-key:a").performTouchInput {
                down(center)
                moveTo(center + Offset(70f, 0f), 50)
                moveTo(center + Offset(110f, 0f), 50)
                up()
            }
            swipe()
            rule.runOnIdle { assertTrue(moves.isEmpty()); assertTrue(letters.isEmpty()) }
            rule.runOnIdle { prefs.edit().putString("cursor_gesture_mode", "KEYBOARD").commit() }
            rule.waitForIdle()
            swipe()
            rule.runOnIdle { assertTrue("explicit keyboard mode must move", moves.sum() > 0); assertTrue(letters.isEmpty()) }
        } finally {
            prefs.edit().apply { if (old == null) remove("cursor_gesture_mode") else putString("cursor_gesture_mode", old) }.commit()
        }
    }
}
