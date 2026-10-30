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

    @Test fun shoudaoFollowsItsKeyMap() {
        val s = DoublePinyinScheme.SHOUDAO
        assertEquals(emptyList<String>(), s.problems())
        // 紫框：zh→V、ch→I、sh→E；零声母 12 组独立双键。
        assertEquals(listOf("vi", "ii", "ei"), listOf("zhi", "chi", "shi").map { s.code(it) })
        assertEquals(listOf("aa", "ai", "an", "ay", "ao", "ue", "ui", "en", "uf", "er", "oo", "ou"),
            listOf("a", "ai", "an", "ang", "ao", "e", "ei", "en", "eng", "er", "o", "ou").map { s.code(it) })
        // 口诀字所在的键：美尊红票样，娃（ua）向团；恳求来年，搜到风怪。
        assertEquals(listOf("mm", "zz", "hh", "pp", "yy", "hw", "xx", "tt", "kk", "qq", "ll", "nn", "ss", "dd", "ff", "gg"),
            listOf("mei", "zun", "hong", "piao", "yang", "hua", "xiang", "tuan", "ken", "qiu", "lai", "nian", "sou", "dao", "feng", "guai")
                .map { s.code(it) })
        // ue（jue）在 L，üe（lüe）在 B，ü 和 ui 在 V。
        assertEquals(listOf("jl", "lb", "lv", "dv", "ju", "xh", "ex"),
            listOf("jue", "lve", "lv", "dui", "ju", "xiong", "shuang").map { s.code(it) })
        assertEquals(mapOf("dk" to listOf("dia", "den"), "lo" to listOf("luo", "lo")), s.collisions())
        assertEquals("ex'pc", s.preview())
    }

    @Test fun shoudaoPreeditAndMixedEnglish() {
        val s = DoublePinyinScheme.SHOUDAO
        val rules = s.preeditFormat()
        assertEquals("zhong guo", apply(rules, "vhgo"))
        assertEquals("eng e ang ei er", apply(rules, "ufueayuier"))
        assertEquals("lve jue", apply(rules, "lbjl"))
        assertEquals("zhong sh", apply(rules, "vhe"))
        val table = s.cnEnTable("#@/db_name\tcn_en_flypy.txt\nX光\tXgl\nB站\tbvj\n4S店\tsiSdm\n").lines()
        assertEquals(listOf("#@/db_name\tcn_en_double_pinyin_shoudao.txt", "X光\tXgx", "B站\tbvj", "4S店\tsiSdn"), table.take(4))
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

    /** 带 derive 的拼写运算：derive 保留原拼写并加上改写后的拼写。 */
    private fun spellings(rules: List<String>, input: String): Set<String> = rules.fold(setOf(input)) { set, rule ->
        val parts = rule.split('/')
        if (parts[0] == "derive") set + set.map { Regex(parts[1]).replace(it, parts[2]) }
        else set.map { apply(listOf(rule), it) }.filter { it.isNotEmpty() }.toSet()
    }

    @Test fun t9VersionTypesEachSyllableOnTwoDigitKeys() {
        val digits = mapOf("abc" to '2', "def" to '3', "ghi" to '4', "jkl" to '5', "mno" to '6', "pqrs" to '7', "tuv" to '8', "wxyz" to '9')
        fun t9(code: String) = code.map { c -> digits.entries.first { c in it.key }.value }.joinToString("")
        for (scheme in DoublePinyinScheme.BUILTINS + custom) {
            val id = DoublePinyinScheme.t9Id(scheme.id)
            val text = scheme.t9Schema()
            assertTrue(text.startsWith(DoublePinyinScheme.GENERATED_MARK + id + "\n"))
            assertTrue(text.contains("  schema_id: $id\n  language: zh\n  input_scheme: double_pinyin\n"))
            assertTrue(text.contains("  prism: $id\n"))
            assertTrue(text.contains("  left_panel_mode: none\n  double_pinyin: true\n  isDisplayOriginalPreedit: false\n"))
            assertTrue(text.contains("    - t9_processor\n") && text.contains("    - t9_filter\n"))
            assertFalse(text.contains("joint_decoder") || text.contains("cylm"))
            val rules = text.substringAfter("  algebra:\n").lines().takeWhile { it.startsWith("    - ") }
                .map { it.removePrefix("    - \"").removeSuffix("\"").replace("\\\"", "\"").replace("\\\\", "\\") }
            assertEquals(scheme.algebra().size + 8, rules.size)
            for (syllable in DoublePinyinScheme.SYLLABLES) {
                val code = scheme.code(syllable)!!
                val result = spellings(rules, syllable)
                assertTrue("$syllable $result", code in result && t9(code) in result)
                assertTrue("$syllable $result", result.all { it.length == 2 })
            }
        }
        assertEquals("87", t9(DoublePinyinScheme.FLYPY.code("zhong")!!))
        assertEquals("小鹤双拼九键", DoublePinyinScheme.FLYPY.t9Schema().substringAfter("  name: \"").substringBefore('"'))
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
