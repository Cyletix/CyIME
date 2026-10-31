package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsTest {
    private val items = Credits.groups.flatMap { it.second }

    @Test fun everyItemOpensAnHttpsPage() {
        val bad = items.filterNot { it.url.startsWith("https://") }.map { it.name }
        assertTrue("不是 https 链接：$bad", bad.isEmpty())
    }

    @Test fun namesAreUnique() {
        assertEquals(items.size, items.map { it.name }.toSet().size)
    }

    /** 用户点名要写清楚的来源（10-31）：fork 来源、LCCC 语料、雾凇、三星键盘、Gboard、首道双拼。 */
    @Test fun namedSourcesAreListed() {
        val names = items.map { it.name }
        for (name in listOf("Xime", "LCCC 中文对话语料", "雾凇拼音", "三星键盘", "Gboard", "首道双拼"))
            assertTrue("缺少 $name", name in names)
    }

    /** 设计参考只是参考，不写许可证；其余各项都要写。 */
    @Test fun onlyDesignReferencesHaveNoLicense() {
        Credits.groups.forEach { (group, list) ->
            list.forEach { assertEquals("$group / ${it.name}", group == "设计参考", it.license == null) }
        }
    }
}
