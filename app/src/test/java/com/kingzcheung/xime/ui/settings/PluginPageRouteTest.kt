package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 11-03「界面跳转极其混乱」：一个插件只有一页，所有入口都进它；只有已下架的进详情页。 */
class PluginPageRouteTest {
    private val aiRewrite = "com.kingzcheung.xime.plugin.ai_rewrite"
    private val retired = "com.kingzcheung.xime.plugin.online_asr"

    @Test fun everyEntryOpensTheSamePage() {
        val page = "${SettingsRoutes.PluginSettings}/$aiRewrite"
        assertEquals(page, pluginPageRoute(aiRewrite))
        // 联网被拦、面板「设置」
        assertEquals(page, MainActivity.pluginLaunchRoute(aiRewrite, settings = false))
        assertEquals(page, MainActivity.pluginLaunchRoute(aiRewrite, settings = true))
        // 键盘里长按插件按钮
        val button = ToolbarButtonItem.Plugin("$aiRewrite:open", "AI 改写", null, aiRewrite, "open_panel")
        assertEquals(page, menuSettingsEntry(button)?.target)
    }

    @Test fun retiredPluginsOpenTheDetailPage() {
        assertEquals("plugin_detail/$retired", pluginPageRoute(retired))
        assertEquals("plugin_detail/$retired", MainActivity.pluginLaunchRoute(retired, settings = true))
    }
}
