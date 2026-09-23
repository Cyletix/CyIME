package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class ChineseAssetMigrationPolicyTest {
    @Test fun dictionariesNeverEnterTheStartupComparisonPathEvenWhenSmall() {
        listOf("rime_ice.dict.yaml", "cn_dicts/tencent.dict.yaml", "en_dicts/en.dict.yaml").forEach {
            assertFalse(ChineseSchemas.isSmallMigrationCandidate(it, 100))
            assertFalse(ChineseSchemas.isSmallMigrationCandidate(it, 20_000_000))
        }
    }
    @Test fun hugeConfigurationOrLuaFilesCannotTriggerLargeStartupAllocations() {
        listOf("t9_pinyin.schema.yaml", "pinyin_14jian.schema.yaml", "lua/uuid.lua").forEach {
            assertTrue(ChineseSchemas.isSmallMigrationCandidate(it, 2048))
            assertFalse(ChineseSchemas.isSmallMigrationCandidate(it, 262145))
        }
    }
    @Test fun userPatchesAndResourcesAreNeverMigrationCandidates() {
        listOf("rime_ice.custom.yaml", "opencc/emoji.txt", "README.md", "LICENSE").forEach {
            assertFalse(ChineseSchemas.isSmallMigrationCandidate(it, 2048))
        }
    }
}
