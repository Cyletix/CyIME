package com.kingzcheung.xime.settings

import com.kingzcheung.xime.rime.flypySyllable
import org.junit.Assert.*
import org.junit.Test

class DoublePinyinSchemeTest {
    /** Rime 拼写运算里用到的三种规则，按顺序作用在一个字符串上（与 librime 的 xform/xlit/erase 同义）。 */
    private fun apply(rules: List<String>, input: String): String {
        var text = input
        for (rule in rules) {
            val parts = rule.split('/')
            when (parts[0]) {
                "erase" -> if (Regex(parts[1]).matches(text)) return ""
                "xform" -> text = Regex(parts[1]).replace(text, parts[2])
                "xlit" -> text = text.map { c -> parts[1].indexOf(c).let { if (it >= 0) parts[2][it] else c } }.joinToString("")
                else -> fail(rule)
            }
        }
        return text
    }

    /** 自定义方案的例子：小鹤双拼把 sh 改到 E 键。 */
    private val custom = DoublePinyinScheme.fresh(DoublePinyinScheme.FLYPY, "我的双拼").let {
        it.copy(initials = it.initials + ("sh" to 'e'))
    }

    @Test fun flypyTableMatchesTheShippedFlypyRules() {
        for (syllable in DoublePinyinScheme.SYLLABLES)
            assertEquals(syllable, flypySyllable(syllable), DoublePinyinScheme.FLYPY.code(syllable))
    }

    @Test fun algebraTurnsEverySyllableIntoItsCode() {
        for (scheme in DoublePinyinScheme.BUILTINS + custom) {
            val rules = scheme.algebra()
            for (syllable in DoublePinyinScheme.SYLLABLES) assertEquals(syllable, scheme.code(syllable), apply(rules, syllable))
        }
    }

    @Test fun preeditShowsPinyin() {
        val rules = custom.preeditFormat()
        assertEquals("zhong guo", apply(rules, "vsgo"))
        assertEquals("zhong guo", apply(rules, "vs go"))
        assertEquals("shuang'pin", apply(rules, "el'pb"))
        assertEquals("eng e ang ei", apply(rules, "egeeahei"))
        assertEquals("lve jue", apply(rules, "ltjt"))
        // 最后一个只打了声母键时显示声母。
        assertEquals("zhong sh", apply(rules, "vse"))
        assertEquals("Txu", apply(rules, "Txu"))
    }

    @Test fun invalidKeysAreReported() {
        assertTrue(custom.valid())
        assertFalse(custom.copy(initials = custom.initials + ("zh" to 'b')).valid())
        assertFalse(custom.copy(initials = custom.initials + ("zh" to 'i')).valid())
        assertFalse(custom.copy(finals = custom.finals - "iang").valid())
        assertFalse(custom.copy(zero = custom.zero + ("ang" to "a")).valid())
        assertFalse(custom.copy(name = " ").valid())
        assertFalse(custom.copy(id = "double_pinyin_flypy").valid())
    }

    @Test fun schemaReplacesOnlyTheEncoding() {
        val base = """
            # Rime schema
            schema:
              schema_id: double_pinyin_flypy
              name: 小鹤双拼
              version: "1"
              author:
                - Dvel
              description: |
                雾凇拼音 - 小鹤双拼
              dependencies:
                - melt_eng

            translator:
              dictionary: rime_ice
              prism: double_pinyin_flypy    # 注释
              preedit_format:               # 注释
                - xform/(^|[ '])aa/${'$'}1a/
                - xform/(\w)g/${'$'}1eng/
              # dictionary_exclude: []

            cn_en:
              user_dict: en_dicts/cn_en_flypy

            speller:
              alphabet: zyxwvutsrqponmlkjihgfedcba
              algebra:
                - erase/^xx${'$'}/
                - xform/iu${'$'}/Ⓠ/
                # - abbrev/^(.).+${'$'}/${'$'}1/
            """.trimIndent() + "\n"
        val text = custom.schema(base)
        assertTrue(text.startsWith(DoublePinyinScheme.GENERATED_MARK + custom.id + "\n"))
        assertTrue(text.contains("  schema_id: ${custom.id}\n  language: zh\n  input_scheme: double_pinyin\n"))
        assertTrue(text.contains("  name: \"我的双拼\"\n"))
        assertTrue(text.contains("  author:\n    - Dvel\n"))
        assertTrue(text.contains("  prism: ${custom.id}\n"))
        assertTrue(text.contains("  user_dict: en_dicts/cn_en_${custom.id}\n"))
        assertTrue(text.contains("  dictionary: rime_ice\n"))
        assertTrue(text.contains("  # dictionary_exclude: []\n"))
        assertFalse(text.contains("xform/iu${'$'}/Ⓠ/") || text.contains("abbrev") || text.contains("(\\w)g"))
        val algebra = text.substringAfter("  algebra:\n").lines().takeWhile { it.startsWith("    - ") }
        assertEquals(custom.algebra().size, algebra.size)
        assertEquals("    - \"xform/(?<=[A-Z])\\\\s(?=[A-Z])//\"", text.substringAfter("  preedit_format:\n")
            .lines().takeWhile { it.startsWith("    - ") }.last())
    }

    @Test fun mixedEnglishTableFollowsTheScheme() {
        val flypy = "# Rime table\n#@/db_name\tcn_en_flypy.txt\n# 此行之后不能写注释\nX光\tXgl\n4S店\tsiSdm\nJ.K.罗琳\tJKlolb\n上网\tuhwh\n坏\tz\n"
        val table = custom.cnEnTable(flypy).lines()
        assertEquals("#@/db_name\tcn_en_${custom.id}.txt", table[1])
        assertEquals(listOf("X光\tXgl", "4S店\tsiSdm", "J.K.罗琳\tJKlolb", "上网\tehwh"), table.drop(3).take(4))
        // 拆不开的行（编码与汉字数对不上）跳过。
        assertFalse(table.any { it.startsWith("坏") })
        assertEquals(8, table.size)
    }
}
