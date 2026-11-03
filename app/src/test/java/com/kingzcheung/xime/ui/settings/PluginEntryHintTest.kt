package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginEntryHintTest {
    @Test fun hintNamesWhereTheButtonIs() {
        assertEquals("入口：键盘菜单 › AI 改写", pluginEntryHintText("AI 改写", "menu"))
        assertEquals("入口：键盘工具栏 › AI 改写", pluginEntryHintText("AI 改写", "toolbar"))
        assertEquals("入口：键盘菜单 › 排列，把「AI 改写」拖进工具栏或菜单", pluginEntryHintText("AI 改写", "unused"))
        assertEquals("入口：打开开关后，键盘菜单 › AI 改写", pluginEntryHintText("AI 改写", null))
    }
}
