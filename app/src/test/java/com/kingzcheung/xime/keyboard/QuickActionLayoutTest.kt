package com.kingzcheung.xime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionLayoutTest {
    @Test fun existingToolbarIsKeptAndDefaultMenuSkipsItsEntries() {
        val toolbar = listOf("schema", "emoji", "ocr", "clipboard")
        val layout = QuickActionLayout.normalize(toolbar, QuickActionLayout.defaultMenu(toolbar))
        assertEquals(toolbar, layout.toolbar)
        assertEquals(listOf("settings", "float", "sound", "glass", "incognito", "full_shape", "floating", "traditional", "arrange"), layout.menu.filter { it !in ToolbarButton.LAYOUT_GESTURES.map { g -> g.id } })
        // 字幕是插件的按钮：新装的默认菜单里没有，装了字幕插件才放进去。
        assertTrue("caption" !in QuickActionLayout.defaultMenu(emptyList()))
    }

    @Test fun eachEntryLivesInOnePlaceAndSettingsCanBeHidden() {
        val layout = QuickActionLayout.normalize(listOf("emoji", "emoji", "glass"), listOf("glass", "ocr"))
        assertEquals(listOf("emoji", "glass"), layout.toolbar)
        assertEquals(listOf("ocr"), layout.menu)
    }

    @Test fun draggingAcrossDividersMovesBetweenToolbarMenuAndUnused() {
        val start = QuickActionLayout(listOf("emoji", "voice"), listOf("settings", "sound"))
        val order = start.editorOrder(listOf("emoji", "voice", "settings", "sound", "glass", "ocr"))
        assertEquals(listOf("emoji", "voice", "#menu", "settings", "sound", "#unused", "glass", "ocr"), order)
        // voice down into the menu, glass up onto the toolbar, settings dropped into unused.
        val edited = listOf("emoji", "glass", "#menu", "voice", "sound", "#unused", "settings", "ocr")
        val layout = QuickActionLayout.fromEditorOrder(edited)
        assertEquals(listOf("emoji", "glass"), layout.toolbar)
        assertEquals(listOf("voice", "sound"), layout.menu)
        assertTrue("unused entries are not stored", "ocr" !in layout.toolbar + layout.menu)
    }

    /** 排列页只列当前键盘会出现的入口；别的键盘专用的入口保存后仍在原来的区和位置。 */
    @Test fun hiddenEntriesKeepTheirPlaceWhenArrangingInAnotherLayout() {
        val saved = QuickActionLayout(listOf("schema", "emoji"), listOf("settings", "t9_flick", "sound", "flick_disc", "arrange"))
        val hidden = setOf("t9_flick", "flick_disc")
        // 在 26 键里排列：只看到 settings/sound/arrange，把 sound 拖到最前，emoji 拖进菜单
        val edited = QuickActionLayout(listOf("schema"), listOf("sound", "emoji", "settings", "arrange"))
        val result = QuickActionLayout.restoreHidden(edited, saved, hidden)
        assertEquals(listOf("schema"), result.toolbar)
        assertEquals(listOf("sound", "t9_flick", "emoji", "flick_disc", "settings", "arrange"), result.menu)
        // 什么都不动时保存，原样不变
        assertEquals(saved, QuickActionLayout.restoreHidden(
            QuickActionLayout(saved.toolbar, saved.menu - hidden), saved, hidden))
    }

    @Test fun enabledPluginButtonGoesIntoMenuBeforeArrange() {
        val plugin = "com.example.plugin:open"
        val menu = listOf("settings", "ocr", "arrange")
        assertEquals(listOf("settings", "ocr", plugin, "arrange"),
            QuickActionLayout.withPluginButtons(listOf("emoji"), menu, listOf(plugin)))
        // 已经在工具栏或菜单里的不动，也不重复
        assertEquals(menu, QuickActionLayout.withPluginButtons(listOf(plugin), menu, listOf(plugin)))
        assertEquals(listOf("settings", plugin), QuickActionLayout.withPluginButtons(emptyList(), listOf("settings", plugin), listOf(plugin)))
        // 菜单里没有「排列」就放最后
        assertEquals(listOf("settings", plugin), QuickActionLayout.withPluginButtons(emptyList(), listOf("settings"), listOf(plugin, "")))
    }
}
