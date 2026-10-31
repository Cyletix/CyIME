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
        assertEquals(listOf("rime_ice", "t9_pinyin"), CyimeInputDefaults.recommended)
        assertFalse(CyimeInputDefaults.recommended.any { it in CyimeInputDefaults.dependencies })
    }
    @Test fun builtinDoublePinyinGoesRightAfterFullPinyinWithoutMovingOtherChoices() {
        val dp = CyimeInputDefaults.doublePinyin
        assertEquals(listOf("double_pinyin_flypy", "double_pinyin_shoudao", "double_pinyin_ziranma",
            "double_pinyin_znabc", "double_pinyin_jiajia"), dp)
        val available = setOf("rime_ice", "t9_pinyin", "japanese") + dp
        assertEquals(listOf("rime_ice") + dp + listOf("t9_pinyin", "japanese"),
            CyimeInputDefaults.withDoublePinyin(listOf("rime_ice", "t9_pinyin", "japanese"), available))
        // 已经开着小鹤的不重复、不挪位置；没有中文26键时排在最后。
        assertEquals(listOf("t9_pinyin", "double_pinyin_flypy") + dp.drop(1),
            CyimeInputDefaults.withDoublePinyin(listOf("t9_pinyin", "double_pinyin_flypy"), available))
    }
    @Test fun doublePinyinWaitsUntilItsSchemasAreGeneratedAndBringsFourteenKeyCompanions() {
        val dp = CyimeInputDefaults.doublePinyin
        assertNull(CyimeInputDefaults.withDoublePinyin(listOf("rime_ice"), setOf("rime_ice", "double_pinyin_flypy")))
        val k14 = dp.map { DoublePinyinScheme.k14Id(it) }
        val available = setOf("rime_ice", "pinyin_14jian") + dp + k14
        assertEquals(listOf("rime_ice") + dp + listOf("pinyin_14jian") + k14,
            CyimeInputDefaults.withDoublePinyin(listOf("rime_ice", "pinyin_14jian"), available))
    }
}
