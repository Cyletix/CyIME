package com.kingzcheung.xime.settings

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TranslatorAppendPatchTest {
    @get:Rule val temp = TemporaryFolder()
    private val phrase = "table_translator@custom_phrase"
    private val pack = "user_cyime_cells_0123456789abcdef"
    private fun map(text: String) = requireNotNull((SchemaManager.yaml.parseToYamlNode(text) as YamlMap).get<YamlMap>("patch"))
    private fun translators(text: String) = map(text).entries.filter { it.key.content.startsWith("engine/translators/") }
        .flatMap { (_, v) -> if (v is YamlList) v.items.map { (it as YamlScalar).content } else listOf((v as YamlScalar).content) }
    private fun addPhrase(file: File) = PersonalDictManager.applyCustomPhraseTranslator(file.parentFile, "rime_ice", "custom_phrase")

    @Test fun bothEnableOrdersKeepTheManualTranslatorAndPhraseWhenCellsAreDisabled() {
        val file = temp.root.resolve("rime_ice.custom.yaml")
        val user = "patch:\n  # my comment\n  engine/translators/+: [script_translator@mine]\n  menu/page_size: 8\n"
        for (cellFirst in listOf(false, true)) {
            file.writeText(if (cellFirst) CellDictionaryRime.patch(user, pack, CellDictionaryEnglish.Kind.ALPHABETIC) else user)
            addPhrase(file)
            if (!cellFirst) file.writeText(CellDictionaryRime.patch(file.readText(), pack, CellDictionaryEnglish.Kind.ALPHABETIC))
            val applied = file.readText()
            assertEquals(listOf(phrase, "script_translator@mine", "table_translator@cyime_cells_en").sorted(), translators(applied).sorted())
            addPhrase(file)
            assertEquals(applied, file.readText())
            val off = CellDictionaryRime.patch(applied, null)
            assertEquals(setOf(phrase, "script_translator@mine"), translators(off).toSet())
            assertTrue(off.contains("menu/page_size: 8") && off.contains("# my comment"))
        }
    }

    @Test fun installedDuplicateAtLineTwoIsRepairedWithoutChangingThePhraseFile() {
        val file = temp.root.resolve("rime_ice.custom.yaml")
        val cell = CellDictionaryRime.patch("patch:\n  menu/page_size: 8\n", pack, CellDictionaryEnglish.Kind.ALPHABETIC)
        val damaged = cell.replaceFirst("patch:\n", "patch:\n  \"engine/translators/+\":\n    - $phrase\n")
        file.writeText(damaged)
        val entries = temp.root.resolve("custom_phrase.txt").apply { writeText("验收短语\tceshi\t99\n") }
        val error = runCatching { map(damaged) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("line 2, column 3"))
        PersonalDictManager.repairTranslatorFile(file)
        val fixed = file.readText()
        assertTrue(fixed.contains("menu/page_size: 8"))
        assertEquals(listOf(phrase, "table_translator@cyime_cells_en").sorted(), translators(fixed).sorted())
        assertEquals(damaged, temp.root.resolve("rime_ice.custom.yaml.before-translator-repair").readText())
        PersonalDictManager.repairTranslatorFile(file)
        assertEquals(fixed, file.readText())
        assertEquals("验收短语\tceshi\t99\n", entries.readText())
    }
}
