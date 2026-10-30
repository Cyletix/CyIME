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

    /** 自然码、自定义双拼的 14 键版本和 14 键小鹤双拼一样：双拼方案和 14 键都启用时自动提供，排在各自的双拼方案后面。 */
    @Test fun everyShuangpinSchemeGetsItsFourteenKeyVersion() {
        val ziranma = DoublePinyinScheme.ZIRANMA_ID
        val custom = DoublePinyinScheme.CUSTOM_PREFIX + "0".repeat(32)
        val all = installed + listOf(ziranma, "${ziranma}_14jian", custom, "${custom}_14jian")
        assertEquals(listOf(ziranma, "double_pinyin_flypy", "pinyin_14jian", custom,
            "${ziranma}_14jian", "double_pinyin_flypy_14jian", "${custom}_14jian"),
            CyimeInputDefaults.withCompanions(listOf(ziranma, "double_pinyin_flypy", "pinyin_14jian", custom), all))
        // 隐藏自然码后它的 14 键版本跟着去掉；14 键关掉时全部去掉。
        assertEquals(listOf("double_pinyin_flypy", "pinyin_14jian", "double_pinyin_flypy_14jian"),
            CyimeInputDefaults.withCompanions(listOf("double_pinyin_flypy", "pinyin_14jian", "${ziranma}_14jian"), all))
        assertEquals(listOf(ziranma, custom),
            CyimeInputDefaults.withCompanions(listOf(ziranma, custom, "${custom}_14jian"), all))
        // 九键版本不是组合配置，照旧单独开关。
        assertEquals(null, CyimeInputDefaults.companionNeeds("${ziranma}_t9"))
        assertEquals(listOf(ziranma, "pinyin_14jian"), CyimeInputDefaults.companionNeeds("${ziranma}_14jian"))
    }

    @Test fun profileMapsGeneratedFourteenKeyVersions() {
        for (id in listOf("${DoublePinyinScheme.ZIRANMA_ID}_14jian", "${DoublePinyinScheme.SHOUDAO_ID}_14jian",
            "${DoublePinyinScheme.CUSTOM_PREFIX}${"a".repeat(32)}_14jian")) {
            val profile = InputProfiles.describe(id)
            assertEquals(id, InputLayout.MERGED14, profile.layout)
            assertEquals(id, InputScheme.DOUBLE_PINYIN, profile.scheme)
            assertEquals(id, InputLanguage.CHINESE, profile.language)
        }
        assertEquals(InputLayout.QWERTY, InputProfiles.describe(DoublePinyinScheme.ZIRANMA_ID).layout)
        // 不是双拼方案的同名后缀不算。
        assertEquals(InputScheme.EXTERNAL, InputProfiles.describe("someone_else_14jian").scheme)
    }
}
