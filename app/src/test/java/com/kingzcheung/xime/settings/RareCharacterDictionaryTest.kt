package com.kingzcheung.xime.settings

import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Rare characters generated from rime-ice 41448.dict.yaml (tasks-chinese.gradle.kts, user 2026-10-30: 瀺 was missing). */
class RareCharacterDictionaryTest {
    private val root = File("build/generated/chinese-assets/rime_ice")

    @Test fun offByDefaultAndOutsideTheMainDictionary() {
        assertFalse(KeyboardInputPreferences().rareCharacters)
        // Imported into rime_ice the list cost the nine-key replay +140% dictionary entries per key.
        assertFalse("cyime_rare" in DictionaryLookup.importTables(File(root, "rime_ice.dict.yaml")))
        assertFalse(File(root, "cn_dicts/41448.dict.yaml").exists())
        assertTrue(File(root, "rime_ice.schema.yaml").readText().contains("    - lua_translator@*cyime_rare_chars\n"))
        assertTrue(File(root, "lua/cyime_rare_chars.lua").isFile)
        assertFalse(File("src/main/assets/rime_chinese/t9_pinyin.schema.yaml").readText().contains("cyime_rare_chars"))
    }

    @Test fun onlyMissingBmpCharactersWithKnownReadings() {
        val common = File(root, "cn_dicts/8105.dict.yaml").readLines().dropWhile { it.trimEnd() != "..." }.drop(1)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val commonText = common.map { it[0] }.toSet()
        val commonCodes = common.map { it[1] }.toSet()
        val rare = File(root, "cyime_rare.txt").readLines().filter { it.isNotEmpty() }.map { it.split('\t') }
        assertTrue(listOf("瀺", "chan") in rare)
        rare.forEach { row ->
            assertEquals(row.toString(), 2, row.size)
            assertEquals(row.toString(), 1, row[0].length)
            assertTrue(row.toString(), Character.isIdeographic(row[0][0].code))
            assertFalse(row.toString(), row[0] in commonText)
            assertTrue(row.toString(), row[1] in commonCodes)
        }
    }
}
