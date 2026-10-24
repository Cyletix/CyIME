package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionaryLookupTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `follows import tables and reads codes`() {
        val dir = folder.root
        java.io.File(dir, "cn_dicts").mkdirs()
        java.io.File(dir, "rime_ice.dict.yaml").writeText(
            "---\nname: rime_ice\nimport_tables:\n  - cn_dicts/base     # 基础\n  # - cn_dicts/off\n  - extra\n...\n")
        java.io.File(dir, "cn_dicts/base.dict.yaml").writeText("---\nname: base\n...\n你好\tni hao\t100\n你\tni\t5\n")
        java.io.File(dir, "extra.dict.yaml").writeText("---\nname: extra\n...\n你好\tnin hao\t1\n")
        val tables = DictionaryLookup.builtInTables(dir)
        assertEquals(listOf("rime_ice.dict.yaml", "base.dict.yaml", "extra.dict.yaml"), tables.map { it.name })
        assertEquals(listOf("ni hao"), DictionaryLookup.codes(tables[1], "你好"))
        assertEquals(emptyList<String>(), DictionaryLookup.codes(tables[1], "你们"))
    }
}
