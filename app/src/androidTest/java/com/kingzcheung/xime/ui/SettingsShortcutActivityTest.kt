package com.kingzcheung.xime.ui

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.service.settingsActivityIntent
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.SettingsRoutes
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsShortcutActivityTest {
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
            ActivityScenario.launch<MainActivity>(settingsActivityIntent(context, SettingsRoutes.Theme)).use {
                waitForTitle("主题与定制")
                ActivityScenario.launch<MainActivity>(settingsActivityIntent(context, SettingsRoutes.SpeechToText)).use {
                    waitForTitle("语音转文本")
                }
            }
        } finally {
            SettingsPreferences.setSetupCompleted(context, setup)
        }
    }
}
