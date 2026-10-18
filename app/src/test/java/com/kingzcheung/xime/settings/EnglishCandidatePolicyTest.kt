package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class EnglishCandidatePolicyTest {
    private val old = """
        schema:
          schema_id: custom_pinyin_fixture
          name: 我的布局
        engine:
          translators:
            - script_translator
            - table_translator@melt_eng
          filters:
            - lua_filter@*long_word_filter
            - uniquifier
        speller:
          algebra:
            - xlit/abc/bca/
        translator:
          dictionary: my_dictionary
    """.trimIndent() + "\n"

    @Test fun upgradesOldLayoutWithoutReplacingItsMappingsOrOtherFilters() {
        for (source in listOf(old, old.replace("\n", "\r\n"))) {
            val updated = EnglishCandidatePolicy.install(source)
            assertTrue(updated.contains("cyime_candidate_policy"))
            assertEquals(source, updated.replace(Regex("(?m)^    - cyime_candidate_policy\\r?\\n"), ""))
            assertEquals(updated, EnglishCandidatePolicy.install(updated))
        }
    }
    @Test fun createsFilterListOnlyForAnEnglishTranslator() {
        val noFilters = old.replace("  filters:\n    - lua_filter@*long_word_filter\n    - uniquifier\n", "")
        assertTrue(EnglishCandidatePolicy.install(noFilters).contains("  filters:\n    - cyime_candidate_policy\n"))
        assertEquals(old.replace("table_translator@melt_eng", "other_translator"),
            EnglishCandidatePolicy.install(old.replace("table_translator@melt_eng", "other_translator")))
    }
    @Test fun resourceLabelsDistinguishLayoutAndSourceWithoutChangingTheirIdentity() {
        KeysConfigHelper.clearSchemaBindingsForTest()
        fun info(id: String, name: String) = SchemaInfo(id, name, "", "", "")
        assertEquals("小鹤双拼 · 26键", info("double_pinyin_flypy", "小鹤双拼").resourceLabel)
        assertEquals("小鹤双拼 · 14键", info("double_pinyin_flypy_14jian", "小鹤双拼").resourceLabel)
        assertEquals("Cyletix10 · 自定义", info("custom_pinyin_fixture", "Cyletix10").resourceLabel)
        assertEquals("Cyletix10（实验） · 内置", info(Cyletix10Layout.ID, "Cyletix10（实验）").resourceLabel)
    }
}
