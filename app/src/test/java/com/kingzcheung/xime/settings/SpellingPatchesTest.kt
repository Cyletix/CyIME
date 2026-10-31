package com.kingzcheung.xime.settings

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import org.junit.Assert.*
import org.junit.Test

class SpellingPatchesTest {
    private val schema = """
        |schema:
        |  schema_id: rime_ice
        |speller:
        |  alphabet: zyxwvutsrqponmlkjihgfedcba
        |  algebra:
        |    - erase/^hm$/
        |    - abbrev/^([a-z]).+$/${'$'}1/
        |    - derive/^([nl])ve$/${'$'}1ue/
        |translator:
        |  dictionary: rime_ice
        |""".trimMargin()

    private fun algebraIn(custom: String): List<String> {
        val root = SchemaManager.yaml.parseToYamlNode(custom) as YamlMap
        val patch = root.entries.entries.single { it.key.content == "patch" }.value as YamlMap
        val list = patch.entries.entries.single { it.key.content == "speller/algebra" }.value as YamlList
        return list.items.map { (it as YamlScalar).content }
    }

    @Test fun readsTheSchemaAlgebraInOrder() {
        assertEquals(listOf("erase/^hm$/", "abbrev/^([a-z]).+$/\$1/", "derive/^([nl])ve$/\$1ue/"),
            SpellingPatches.algebraOf(schema))
        assertNull(SpellingPatches.algebraOf("translator:\n  dictionary: x\n"))
    }

    @Test fun fuzzyRulesComeBeforeTheOriginalRulesAndSurviveYamlQuoting() {
        val algebra = SpellingPatches.algebraOf(schema)!!
        val items = linkedSetOf(SpellingPatches.Fuzzy.Z, SpellingPatches.Fuzzy.AN)
        val custom = SpellingPatches.patch("", SpellingPatches.fuzzyLines(algebra, items))!!
        assertEquals(SpellingPatches.Fuzzy.Z.rules + SpellingPatches.Fuzzy.AN.rules + algebra, algebraIn(custom))
    }

    @Test fun otherPatchesAreKeptAndTurningEverythingOffRestoresTheFile() {
        val original = "patch:\n  \"engine/translators/+\":\n    - table_translator@custom_phrase\n  custom_phrase:\n    user_dict: custom_phrase\n"
        val lines = SpellingPatches.fuzzyLines(listOf("erase/^hm$/"), setOf(SpellingPatches.Fuzzy.NL))
        val patched = SpellingPatches.patch(original, lines)!!
        assertTrue(patched.contains("table_translator@custom_phrase"))
        assertEquals(listOf("derive/^n/l/", "derive/^l/n/", "erase/^hm$/"), algebraIn(patched))
        // 再写一次只换掉自己那一段，不重复。
        val again = SpellingPatches.patch(patched, lines)!!
        assertEquals(patched, again)
        assertEquals(original, SpellingPatches.patch(patched, emptyList()))
    }

    @Test fun aFileWithoutPatchGetsOneAndBrokenMarkersAreLeftAlone() {
        val patched = SpellingPatches.patch("# 用户注释\n", SpellingPatches.fuzzyLines(listOf("erase/^hm$/"), setOf(SpellingPatches.Fuzzy.IN)))!!
        assertTrue(patched.startsWith("# 用户注释\npatch:\n"))
        assertEquals(listOf("derive/ing$/in/", "derive/in$/ing/", "erase/^hm$/"), algebraIn(patched))
        assertNull(SpellingPatches.patch("patch:\n  # BEGIN CYIME SPELLING\n", listOf("\"translator/enable_correction\": true")))
    }

    @Test fun anAndAngLeaveIanAndUanToTheirOwnGroups() {
        val toAn = Regex("(^|[^iu])ang$")
        val toAng = Regex("(^|[^iu])an$")
        assertTrue(toAn.containsMatchIn("fang") && toAn.containsMatchIn("ang") && toAn.containsMatchIn("zhang"))
        assertFalse(toAn.containsMatchIn("xiang") || toAn.containsMatchIn("guang"))
        assertTrue(toAng.containsMatchIn("fan") && toAng.containsMatchIn("an") && toAng.containsMatchIn("yan"))
        assertFalse(toAng.containsMatchIn("xian") || toAng.containsMatchIn("guan"))
        assertEquals("derive/(^|[^iu])ang$/\$1an/", SpellingPatches.Fuzzy.AN.rules[0])
    }

    @Test fun everyGroupDerivesBothWaysAndHasAStableId() {
        for (item in SpellingPatches.Fuzzy.entries) {
            assertEquals(item.title, 2, item.rules.size)
            assertTrue(item.title, item.rules.all { it.startsWith("derive/") })
        }
        assertEquals(SpellingPatches.Fuzzy.entries.size, SpellingPatches.Fuzzy.entries.map { it.id }.toSet().size)
    }
}
