package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonCellDictionaryTest {
    @get:Rule val temp = TemporaryFolder()
    private val valid = """{"format":"cyime.dictionary.v1","name":"多音字词组","language":"zh","scheme":"pinyin","entries":[{"word":"银行","code":"yin hang"},{"word":"银行","code":"yin hang"},{"word":"行走","code":"xing zou"}]}"""

    @Test fun `open dictionary format installs in the existing opt-in collection store`() {
        val store = CellDictionaryStore(temp.newFolder())
        val installed = store.install(valid.toByteArray())
        assertEquals("多音字词组", installed.name)
        assertEquals(2, installed.count)
        assertTrue(store.read().selected.isEmpty())
        assertTrue(store.dictionaryFile(installed.id).readText().contains("银行\tyin hang\t100"))
        store.select(installed.id, true)
        assertTrue(store.read().pending)
        assertEquals(installed, store.install(valid.toByteArray()))
    }

    @Test fun `rejects wrong language format control characters and missing syllables`() {
        for (bad in listOf(valid.replace("\"zh\"", "\"ja\""), valid.replace("\"pinyin\"", "\"romaji\""),
            valid.replace("dictionary.v1", "dictionary.v2"), valid.replace("yin hang", "yin"),
            valid.replace("银行", "银\\n行"), valid.replace("yin hang", "yin1 hang2"),
            valid.dropLast(1))) {
            assertThrows(Exception::class.java) { JsonCellDictionary.parse(bad.toByteArray()) }
        }
    }

    @Test fun `raw model reply cannot bypass the export and validation stage`() {
        val reply = """{"format":"cyime.lexicon.v1","entries":[{"word":"银行","pinyin":"yin2 xing2"}]}"""
        assertThrows(Exception::class.java) { JsonCellDictionary.parse(reply.toByteArray()) }
    }

    /** Optional local integration corpus, produced by real Cline calls; tests never call the API. */
    @Test fun `real generated exports install and preserve exactly the validated word codes`() {
        val directory = sequenceOf(File(".codex-artifacts/generated-lexicon-corpus"),
            File("../.codex-artifacts/generated-lexicon-corpus")).firstOrNull { it.isDirectory }
        assumeTrue("Local generated corpus unavailable", directory != null)
        val files = requireNotNull(directory).listFiles { file -> file.name.endsWith(".cyime-dict.json") }!!.sorted()
        assertTrue("No exported collections", files.isNotEmpty())
        val store = CellDictionaryStore(temp.newFolder())
        val counts = files.map { file ->
            val expectedRime = File(directory, file.name.removeSuffix(".cyime-dict.json") + ".dict.yaml")
            val expected = PersonalDictManager.parsePersonalDictEntries(expectedRime.readText())
            assertTrue("Empty ${file.name}", expected.isNotEmpty())
            val installed = store.install(file.readBytes())
            val actual = PersonalDictManager.parsePersonalDictEntries(store.dictionaryFile(installed.id).readText())
            assertEquals(file.name, expected.toSet(), actual.toSet())
            assertEquals(expected.size, installed.count)
            "${file.name}\t${installed.count}"
        }
        assertTrue(store.read().selected.isEmpty())
        File(directory, "parsed-counts.tsv").writeText(counts.joinToString("\n") + "\n")
    }
}
