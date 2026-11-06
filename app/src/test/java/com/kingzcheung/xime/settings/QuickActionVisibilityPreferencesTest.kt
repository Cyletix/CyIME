package com.kingzcheung.xime.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.QuickActionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickActionVisibilityPreferencesTest {
    @Test fun olderLayoutsKeepSettingsUntilTheUserExplicitlyHidesIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("kime_settings", Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPreferences.setToolbarButtons(app, listOf("emoji"))
        app.getSharedPreferences("kime_settings", Context.MODE_PRIVATE).edit().putString("menu_buttons", "sound").apply()
        assertTrue("旧布局原本依靠补全来显示设置", "settings" in SettingsPreferences.getMenuButtons(app))

        SettingsPreferences.setQuickActions(app, QuickActionLayout(listOf("emoji"), listOf("sound")))
        assertEquals(listOf("sound"), SettingsPreferences.getMenuButtons(app))
        SettingsPreferences.setQuickActions(app, QuickActionLayout(listOf("emoji"), emptyList()))
        assertEquals(emptyList<String>(), SettingsPreferences.getMenuButtons(app))
        SettingsPreferences.setQuickActions(app, QuickActionLayout(listOf("emoji"), listOf("settings")))
        assertEquals(listOf("settings"), SettingsPreferences.getMenuButtons(app))
    }
}
