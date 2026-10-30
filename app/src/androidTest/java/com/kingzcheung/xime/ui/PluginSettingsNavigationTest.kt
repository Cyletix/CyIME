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

    @Test fun pluginManagementHasNoStoreAndImportsFromFile() {
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "plugins") } }
        rule.onNodeWithText("插件管理").assertIsDisplayed()
        rule.onNodeWithText("安装插件").assertDoesNotExist()
        rule.onNodeWithContentDescription("更多").performClick()
        rule.onNodeWithText("从文件安装插件").assertIsDisplayed()
    }

    @Test fun clipboardSetupIsReachableBeforeSyncIsEnabled() {
        val original = SettingsPreferences.isClipboardSyncEnabled(rule.activity)
        var manages = 0
        try {
            SettingsPreferences.setClipboardSyncEnabled(rule.activity, false)
            rule.setContent { XimeTheme { ClipboardSyncSettingsContent({}, { manages++ }) } }
            rule.onNodeWithText("附近电脑").assertExists()
            rule.onNodeWithText("安装插件").assertDoesNotExist()
            rule.onNodeWithText("其他服务与旧版兼容连接").performScrollTo().performClick()
            rule.onNodeWithText("管理其他同步服务").performScrollTo().performClick()
            rule.onNodeWithText("同步服务").assertExists()
            assertEquals(1, manages)
        } finally { SettingsPreferences.setClipboardSyncEnabled(rule.activity, original) }
    }

    @Test fun backupIsLocalAndNeedsNoPlugin() {
        rule.setContent { XimeTheme { BackupSettingsContent(onBack = {}) } }
        rule.onNodeWithText("导出备份").assertIsDisplayed()
        rule.onNodeWithText("从备份恢复").assertIsDisplayed()
        rule.onNodeWithText("安装插件").assertDoesNotExist()
    }
    @Test fun onlineSpeechIsBuiltInApiWithoutPluginStore() {
        rule.setContent { XimeTheme { OnlineSpeechApiSection() } }
        rule.onNodeWithTag("speech-api-key").assertIsDisplayed()
        rule.onNodeWithTag("speech-api-test").assertIsDisplayed()
        rule.onNodeWithTag("speech-api-status").assertIsDisplayed()
        rule.onNodeWithText("安装插件").assertDoesNotExist()
    }

}
