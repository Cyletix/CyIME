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
            while (!shown && SystemClock.uptimeMillis() < deadline) {
                shown = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(title)
                    ?.any { it.text?.toString() == title } == true
                if (!shown) SystemClock.sleep(50)
            }
            assertTrue("Shortcut did not open $title", shown)
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
