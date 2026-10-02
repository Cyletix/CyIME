package com.kingzcheung.xime.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.service.settingsActivityIntent
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.SettingsRoutes
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class SettingsShortcutActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun assertResumedInstance(expected: MainActivity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
            assertSame("Settings must reuse its existing activity", expected, resumed.single())
        }
    }

    @Test fun repeatedSettingsKeepsTheTestTextAndActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val setup = SettingsPreferences.isSetupCompleted(context)
        SettingsPreferences.setSetupCompleted(context, true)
        try {
            // Start through the enabled launcher alias, as users do, then reopen from the IME.
            val launcherIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            ActivityScenario.launch<MainActivity>(launcherIntent).use { scenario ->
                lateinit var original: MainActivity
                scenario.onActivity { original = it }
                compose.onNode(hasSetTextAction()).performClick().performTextInput("保留测试内容")
                repeat(3) {
                    context.startActivity(settingsActivityIntent(context))
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    compose.waitForIdle()
                    assertResumedInstance(original)
                    compose.onNode(hasSetTextAction()).assertTextEquals("保留测试内容")
                }
            }
        } finally {
            SettingsPreferences.setSetupCompleted(context, setup)
        }
    }

    @Test fun shortcutReplacesAnAlreadyOpenSettingsPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val setup = SettingsPreferences.isSetupCompleted(context)
        SettingsPreferences.setSetupCompleted(context, true)
        fun waitForTitle(title: String) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = SystemClock.uptimeMillis() + 10_000
            var shown = false
            var visibleText = emptyList<String>()
            fun collectText(node: android.view.accessibility.AccessibilityNodeInfo?, result: MutableList<String>) {
                if (node == null) return
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
                repeat(node.childCount) { collectText(node.getChild(it), result) }
            }
            while (!shown && SystemClock.uptimeMillis() < deadline) {
                // Compose exposes virtual descendants; walk them like UI Automator instead
                // of relying on the platform view-provider text-search implementation.
                visibleText = mutableListOf<String>().also { collectText(automation.rootInActiveWindow, it) }
                shown = title in visibleText
                if (!shown) SystemClock.sleep(50)
            }
            assertTrue("Shortcut did not open $title; visible: $visibleText", shown)
        }
        try {
            ActivityScenario.launch<MainActivity>(settingsActivityIntent(context, SettingsRoutes.Theme)).use { scenario ->
                lateinit var original: MainActivity
                scenario.onActivity { original = it }
                waitForTitle("主题与定制")
                context.startActivity(settingsActivityIntent(context, SettingsRoutes.SpeechToText))
                waitForTitle("语音转文本")
                assertResumedInstance(original)
                context.startActivity(settingsActivityIntent(context, SettingsRoutes.Theme))
                waitForTitle("主题与定制")
                assertResumedInstance(original)
            }
        } finally {
            SettingsPreferences.setSetupCompleted(context, setup)
        }
    }
}
