package com.kingzcheung.xime.ui.menubar

import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.SchemaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 布局面板点一下布局就切换，不用再点方案。 */
class SchemaForLayoutTest {
    private val entries = listOf("rime_ice", "double_pinyin_flypy", "double_pinyin_flypy_14jian", "pinyin_14jian", "t9_pinyin")
        .map { SchemaInfo(it, it, "", "", "") }

    @Test fun keepsCurrentSchemeWhenTheLayoutHasIt() {
        assertEquals("pinyin_14jian", schemaForLayout(entries, "qwerty_14", InputScheme.PINYIN))
        assertEquals("double_pinyin_flypy_14jian", schemaForLayout(entries, "qwerty_14", InputScheme.DOUBLE_PINYIN))
    }

    @Test fun fallsBackToFirstSchemeOfTheLayout() {
        assertEquals("t9_pinyin", schemaForLayout(entries, "t9", InputScheme.DOUBLE_PINYIN))
    }

    /** English and Japanese romaji stay on their own entry when a Chinese letter layout is tapped. */
    @Test fun letterLayoutKeepsEnglishAndRomajiEntries() {
        val all = entries + listOf("pinyin_cyletix10", "pinyin_colemak", "japanese").map { SchemaInfo(it, it, "", "", "") } +
            com.kingzcheung.xime.settings.InputModes.english
        assertEquals(com.kingzcheung.xime.settings.InputModes.ENGLISH, schemaForLayout(listOf(com.kingzcheung.xime.settings.InputModes.english),
            "pinyin_cyletix10", InputScheme.DIRECT, all))
        assertEquals("japanese", schemaForLayout(all.filter { it.schemaId == "japanese" }, "pinyin_colemak", InputScheme.ROMAJI, all))
        assertEquals("pinyin_cyletix10", schemaForLayout(all.filter { it.language == com.kingzcheung.xime.settings.InputLanguage.CHINESE },
            "pinyin_cyletix10", InputScheme.PINYIN, all))
    }

    @Test fun unknownLayoutSwitchesNothing() {
        assertNull(schemaForLayout(entries, "japanese_kana", InputScheme.PINYIN))
    }
}
