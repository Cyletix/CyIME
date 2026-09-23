package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class CyimeInputDefaultsTest {
    @Test fun modesHaveOneEntryPerLayoutWithOneEnglish() {
        val ids = listOf("pinyin_simp", "rime_ice", "t9", "t9_pinyin", "melt_eng", "jaroomaji", "japanese", "japanese_kana", "pinyin_14jian")
        assertEquals(listOf("rime_ice", "t9_pinyin", "japanese", "japanese_kana", "pinyin_14jian"), CyimeInputDefaults.canonicalIds(ids, ids.toSet()))
        val modes = InputModes.available(ids.map { SchemaInfo(it, it, "", "", "") })
        assertEquals(1, modes.count { it.schemaId == InputModes.ENGLISH })
        assertFalse(modes.any { it.schemaId in setOf("melt_eng", "pinyin_simp", "jaroomaji", "t9") })
    }
    @Test fun marketOnlyAndUnknownSchemasKeepTheirIdsAndOrder() {
        val ids = listOf("my_personal", "pinyin_simp", "t9", "jaroomaji", "wanxiang")
        assertEquals(ids, CyimeInputDefaults.canonicalIds(ids, ids.toSet()))
    }
    @Test fun defaultChineseModesShareOneOfflineDictionaryFamily() {
        assertEquals(listOf("t9_pinyin", "rime_ice", "pinyin_14jian", "double_pinyin_flypy", "japanese", "japanese_kana"), CyimeInputDefaults.recommended)
        assertFalse(CyimeInputDefaults.recommended.any { it in CyimeInputDefaults.dependencies })
    }
}
