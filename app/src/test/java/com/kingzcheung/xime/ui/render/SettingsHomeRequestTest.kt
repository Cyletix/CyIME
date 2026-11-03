package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.R
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.ui.settings.SettingsNavigationRequest
import com.kingzcheung.xime.ui.settings.SettingsRoutes
import com.kingzcheung.xime.ui.settings.SettingsScreen
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** 11-03 平板：键盘菜单「设置」在设置已开着时要回到设置首页，不停在上次那一页。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w360dp-h680dp-mdpi")
class SettingsHomeRequestTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setup() {
        File(app.filesDir, "rime/xime.custom.yaml").apply {
            parentFile!!.mkdirs()
            writeText("xime_index:\n  base_urls: []\n")
        }
        KeyboardThemes.initFromConfig(app)
        PluginManager.initialize(app)
    }

    @Test fun homeRequestLeavesAPageOpenedFromHome() {
        var request by mutableStateOf<SettingsNavigationRequest?>(null)
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = SettingsRoutes.KeyEffect, navigationRequest = request) } }
        rule.onNodeWithText("启用按键音").assertIsDisplayed()
        request = SettingsNavigationRequest(SettingsRoutes.Main, 1)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_title)).assertIsDisplayed()
        rule.onNodeWithText("启用按键音").assertDoesNotExist()
    }

    /** 冷启动直接进了插件页（起始页不是首页），之后点「设置」也回到首页。 */
    @Test fun homeRequestReplacesAShortcutStartPage() {
        var request by mutableStateOf<SettingsNavigationRequest?>(null)
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "plugins", navigationRequest = request) } }
        rule.onNodeWithContentDescription("从文件安装插件").assertIsDisplayed()
        request = SettingsNavigationRequest(SettingsRoutes.Main, 1)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription("从文件安装插件").assertDoesNotExist()
    }
}
