package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun titles(q: String) = searchSettings(q).map { it.title }

    @Test fun everydayWordsFindTheSwitch() {
        // 用户以为九键长按坏了：搜“长按”要能找到九键拂动。
        assertTrue("拂动选字母" in titles("长按"))
        assertTrue("数字框自动数字键盘" in titles("验证码"))
        assertTrue("按键效果" in titles("振动"))
    }

    @Test fun titleMatchesComeFirst() {
        assertEquals("拂动选字母", titles("拂动").first())
    }

    @Test fun allTermsMustMatchAndBlankFindsNothing() {
        assertEquals(listOf("假名方向提示"), titles("假名 方向"))
        assertTrue(titles("   ").isEmpty())
        assertTrue(titles("不存在的设置词").isEmpty())
    }

    @Test fun layoutSubPagesExistAndEveryPageIsSearchable() {
        val pages = LayoutDisplayPages.map { it.first }.toSet()
        val linked = SettingsSearchIndex.mapNotNull { it.target.substringAfter("?page=", "").ifEmpty { null } }.toSet()
        assertEquals(pages, linked)
    }

    /** 新设置树：每项只出现一处，路径都从首页五组之一开始。 */
    @Test fun everySettingLivesInOnePlaceUnderTheNewGroups() {
        val dup = SettingsSearchIndex.groupBy { it.title }.filterValues { it.size > 1 }.keys
        assertTrue("重复：$dup", dup.isEmpty())
        val groups = setOf("输入法", "键盘", "智能与输入方式", "扩展", "系统")
        val stray = SettingsSearchIndex.filter { it.path.substringBefore(" › ") !in groups }.map { it.title }
        assertTrue("不在新分组下：$stray", stray.isEmpty())
    }
}
