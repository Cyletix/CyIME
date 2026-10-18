package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 小鹤双拼 + 14 键 → 自动提供 14 键小鹤双拼；缺一项或未安装时不出现。 */
class SchemaCompanionTest {
    private val installed = setOf("rime_ice", "pinyin_14jian", "double_pinyin_flypy", "double_pinyin_flypy_14jian")

    @Test fun shuangpinAndFourteenKeysImplyFourteenKeyShuangpin() {
        assertEquals(listOf("rime_ice", "double_pinyin_flypy", "pinyin_14jian", "double_pinyin_flypy_14jian"),
            CyimeInputDefaults.withCompanions(listOf("rime_ice", "double_pinyin_flypy", "pinyin_14jian"), installed))
    }

    @Test fun companionFollowsItsRequirementsOnly() {
        // 关掉 14 键后，组合配置随之移除，即使旧列表里还有它。
        assertEquals(listOf("double_pinyin_flypy"),
            CyimeInputDefaults.withCompanions(listOf("double_pinyin_flypy", "double_pinyin_flypy_14jian"), installed))
        // 未安装组合文件时不凭空添加。
        assertEquals(listOf("double_pinyin_flypy", "pinyin_14jian"),
            CyimeInputDefaults.withCompanions(listOf("double_pinyin_flypy", "pinyin_14jian"),
                installed - "double_pinyin_flypy_14jian"))
    }

    @Test fun profileMapsTheCompanionToFourteenKeyShuangpin() {
        val profile = InputProfiles.describe("double_pinyin_flypy_14jian")
        assertEquals(InputLayout.MERGED14, profile.layout)
        assertEquals(InputScheme.DOUBLE_PINYIN, profile.scheme)
    }
}
