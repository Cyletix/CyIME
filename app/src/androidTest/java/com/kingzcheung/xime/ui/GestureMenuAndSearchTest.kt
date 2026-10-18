package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.MenuBar
import com.kingzcheung.xime.ui.keyboard.MenuBarState
import com.kingzcheung.xime.ui.keyboard.flipGesture
import com.kingzcheung.xime.ui.keyboard.gestureOn
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.settings.SettingsMainContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 九键菜单里能直接开关拂动选字母；设置首页搜“长按”能找到它并跳到所在页。 */
class GestureMenuAndSearchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nineKeyMenuTogglesFlickLetters() {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.getBoolean("t9_flick_letters", false)
        prefs.edit().putBoolean("t9_flick_letters", false).commit()
        try {
            rule.setContent {
                MaterialTheme {
                    val p = rememberKeyboardInputPreferences()
                    // 与 KeyboardView.quickAction 相同：开关状态取自设置，点一下只改这一项。
                    val actions = listOf(ToolbarButton.T9_FLICK, ToolbarButton.FLICK_DISC).map { b ->
                        ToolbarAction(ToolbarButtonItem.Builtin(b), active = b.gestureOn(p)) { b.flipGesture(context, p) }
                    }
                    MenuBar(MenuBarState(isVisible = true, isDarkTheme = false, backgroundColor = Color(0xFFE8EAED)),
                        actions = actions, modifier = Modifier.fillMaxWidth().height(260.dp))
                }
            }
            rule.onNodeWithTag("menu-item:t9_flick").assertIsDisplayed().assertIsNotSelected()
            rule.onNodeWithTag("menu-item:flick_disc").assertIsDisplayed()
            rule.onNodeWithTag("menu-item:t9_flick").performClick()
            rule.waitUntil(3000) { KeyboardInputPreferences.read(context).t9FlickLetters }
            rule.onNodeWithTag("menu-item:t9_flick").assertIsSelected()
            save("menu-t9")
        } finally {
            prefs.edit().putBoolean("t9_flick_letters", saved).commit()
        }
    }

    @Test fun searchFindsFlickAndOpensItsPage() {
        var opened: String? = null
        rule.setContent {
            MaterialTheme {
                SettingsMainContent(onNavigateToSchema = {}, onNavigateToTheme = {}, onNavigateToKeyEffect = {},
                    onNavigateToLayoutDisplay = {}, onNavigateToDictionary = {}, onNavigateToPlugins = {},
                    onNavigateToSmartPrediction = {}, onNavigateToSpeechToText = {}, onNavigateToAbout = {},
                    onNavigateToRoute = { opened = it })
            }
        }
        rule.onNodeWithTag("settings-search").performTextInput("长按")
        rule.onNodeWithTag("settings-search-result:拂动选字母").assertIsDisplayed()
        save("search-long-press")
        rule.onNodeWithTag("settings-search-result:拂动选字母").performClick()
        rule.runOnIdle { assertEquals("layout_input_nine", opened) }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "gesture-menu-search").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
