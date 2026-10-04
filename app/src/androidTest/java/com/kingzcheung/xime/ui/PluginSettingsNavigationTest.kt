package com.kingzcheung.xime.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.*
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginSettingsNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pluginManagementInstallOpensPluginStoreTabAndBackReturns() {
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "plugins") } }
        rule.onNodeWithText("安装插件").performClick()
        rule.onNodeWithText("扩展商店").assertIsDisplayed()
        rule.onNodeWithText("插件").assertIsSelected()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("插件管理").assertIsDisplayed()
    }

    @Test fun clipboardSetupIsReachableBeforeSyncIsEnabled() {
        val original = SettingsPreferences.isClipboardSyncEnabled(rule.activity)
        var installs = 0
        var manages = 0
        try {
            SettingsPreferences.setClipboardSyncEnabled(rule.activity, false)
            rule.setContent { XimeTheme { ClipboardSyncSettingsContent({}, { manages++ }, { installs++ }) } }
            rule.onNodeWithText("附近电脑").assertExists()
            rule.onNodeWithText("安装插件").assertDoesNotExist()
            rule.onNodeWithText("其他服务与旧版兼容连接").performScrollTo().performClick()
            rule.onNodeWithText("管理其他同步服务").performScrollTo().performClick()
            rule.onNodeWithText("同步服务").assertExists()
            assertEquals(0, installs)
            assertEquals(1, manages)
        } finally { SettingsPreferences.setClipboardSyncEnabled(rule.activity, original) }
    }

    @Test fun backupHasDirectInstallEntry() {
        var installs = 0
        rule.setContent { XimeTheme { BackupSettingsContent({}, {}, { installs++ }) } }
        rule.onNodeWithText("安装插件").assertIsDisplayed().performClick()
        assertEquals(1, installs)
    }
    @Test fun onlineSpeechInstallDoesNotLeadToInstalledOnlyPage() {
        var installs = 0
        rule.setContent { XimeTheme { OnlineAsrTab(emptyList(), {}, onInstallPlugins = { installs++ }) } }
        rule.onNodeWithText("安装插件").assertIsDisplayed().performClick()
        assertEquals(1, installs)
    }

}
