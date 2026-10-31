package com.kingzcheung.xime.settings

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A SCEL file laid out like real downloads: pinyin list count at 0x1540, the word table right after the list,
 * the group count at 0x120. [phrases] (pinyin phrases) and [deleted] words go after the word table with their
 * header offsets, as in newer files.
 */
internal fun scelFixture(
    words: List<String> = listOf("你好", "拟好"),
    variant: Int = 0x44,
    syllables: List<String> = listOf("ni", "hao"),
    code: List<Int> = listOf(0, 1),
    phrases: List<Pair<List<Int>, String>> = emptyList(),
    deleted: List<String> = emptyList(),
    magic: ByteArray = byteArrayOf(variant.toByte(), 0x43, 0x53, 1),
): ByteArray {
    val out = ByteArrayOutputStream()
    fun u16(value: Int) { out.write(value and 255); out.write((value shr 8) and 255) }
    fun u32(value: Int) { u16(value and 0xffff); u16(value ushr 16) }
    out.write(ByteArray(0x1540))
    u32(syllables.size)
    syllables.withIndex().forEach { (id, text) ->
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        u16(id); u16(bytes.size); out.write(bytes)
    }
    u16(words.size); u16(code.size * 2); code.forEach(::u16)
    words.forEach { word ->
        val bytes = word.toByteArray(Charsets.UTF_16LE)
        u16(bytes.size); out.write(bytes); u16(10); out.write(ByteArray(10))
    }
    val phraseStart = out.size()
    phrases.forEach { (indexes, word) ->
        out.write(ByteArray(17).also { it[2] = 1 })
        u16(indexes.size * 2); indexes.forEach(::u16)
        val bytes = word.toByteArray(Charsets.UTF_16LE)
        u16(bytes.size); out.write(bytes)
    }
    val deletedStart = out.size()
    deleted.forEach { word -> u16(word.length); out.write(word.toByteArray(Charsets.UTF_16LE)) }
    val bytes = out.toByteArray()
    fun put32(at: Int, value: Int) { for (i in 0..3) bytes[at + i] = (value ushr (8 * i)).toByte() }
    byteArrayOf(0x40, 0x15, 0, 0).copyInto(bytes)
    magic.copyInto(bytes, 4)
    "测试词库".toByteArray(Charsets.UTF_16LE).copyInto(bytes, 0x130)
    if (phrases.isNotEmpty()) { put32(0x5C, phrases.size); put32(0x60, phraseStart); put32(0x64, deletedStart - phraseStart) }
    if (deleted.isNotEmpty()) { put32(0x74, deleted.size); put32(0x78, deletedStart); put32(0x7C, bytes.size - deletedStart) }
    put32(0x120, 1)
    put32(0x124, words.size)
    return bytes
}

/** Offset of the word table in a default [scelFixture]: 0x1544 + ("ni" 4 + 4) + ("hao" 4 + 6). */
private const val FIXTURE_WORDS = 0x1556

class ScelDictionaryTest {
    @Test fun `both formats preserve pinyin and deduplicate homophones by text and code`() {
        for (version in listOf(0x44, 0x45)) {
            val parsed = ScelDictionary.parse(scelFixture(listOf("你好", "拟好", "你好"), version))
            assertEquals("测试词库", parsed.name)
            assertEquals(listOf(DictEntry("你好", "ni hao", 100), DictEntry("拟好", "ni hao", 100)), parsed.entries)
            val rime = ScelDictionary.toRime("cell_test", parsed.entries)
            assertTrue(rime.contains("你好\tni hao\t100\n"))
        }
    }

    @Test fun `rejects html foreign headers and truncated records`() {
        // Odd word length in the first record; a group that claims more codes than the file holds.
        for (bytes in listOf("<html>access denied</html>".toByteArray(), scelFixture().dropLast(1).toByteArray(),
            scelFixture().also { it[6] = 0x46 }, scelFixture().also { it[FIXTURE_WORDS + 8] = 99 },
            scelFixture().also { it[FIXTURE_WORDS + 2] = 0x7e; it[FIXTURE_WORDS + 3] = 0 })) {
            assertThrows(Exception::class.java) { ScelDictionary.parse(bytes) }
        }
    }

    /** 10-31: an 800 KB download would not import. Real files of these shapes failed before. */
    @Test fun `newer magic and longer pinyin lists import`() {
        val parsed = ScelDictionary.parse(scelFixture(magic = byteArrayOf(0xd2.toByte(), 0x6d, 0x53, 1),
            syllables = listOf("ni", "hao") + ('A'..'Z').map(Char::toString) + ('0'..'9').map(Char::toString)))
        assertEquals(listOf(DictEntry("你好", "ni hao", 100), DictEntry("拟好", "ni hao", 100)), parsed.entries)
    }

    @Test fun `phrases after the word table are words and the deleted list is not`() {
        val parsed = ScelDictionary.parse(scelFixture(variant = 0x45,
            phrases = listOf(listOf(1, 0) to "好你", listOf(0) to "Airbnb"), deleted = listOf("不要的词")))
        assertEquals(listOf("你好", "拟好", "好你"), parsed.entries.map { it.word })
        assertEquals("hao ni", parsed.entries.last().code)
        assertEquals("phrase without a Chinese character", 1, parsed.skipped)
    }

    @Test fun `words whose code has no pinyin are skipped without failing the file`() {
        val digits = scelFixture(syllables = listOf("ni", "hao", "2"), code = listOf(0, 2), words = listOf("你2"),
            phrases = listOf(listOf(0, 1) to "你好"))
        val parsed = ScelDictionary.parse(digits)
        assertEquals(listOf(DictEntry("你好", "ni hao", 100)), parsed.entries)
        assertEquals(1, parsed.skipped)
        // An index past the list (no pinyin at all) is the same.
        assertEquals(1, ScelDictionary.parse(scelFixture(code = listOf(0, 9), words = listOf("坏"),
            phrases = listOf(listOf(0, 1) to "你好"))).skipped)
    }

    @Test fun `empty pinyin list uses the standard numbering`() {
        // 219 = ni, 117 = hao, 105 = gu in Sogou's 413-syllable table; 413 = A.
        val parsed = ScelDictionary.parse(scelFixture(syllables = emptyList(), code = listOf(219, 117),
            phrases = listOf(listOf(413, 105) to "A股")))
        assertEquals(listOf("ni hao", "ni hao", "a gu"), parsed.entries.map { it.code })
    }

    @Test fun `garbage after the name and broken record text do not reject the file`() {
        val bytes = scelFixture(listOf("你好", "拟好", "好"))
        // Lone surrogates after the name's terminator, and in the second word.
        byteArrayOf(0, 0, 0x00, 0xd8.toByte()).copyInto(bytes, 0x130 + 8)
        val second = FIXTURE_WORDS + 8 + (2 + 4 + 2 + 10)
        byteArrayOf(0x00, 0xdc.toByte()).copyInto(bytes, second + 2)
        val parsed = ScelDictionary.parse(bytes)
        assertEquals("测试词库", parsed.name)
        assertEquals(listOf("你好", "好"), parsed.entries.map { it.word })
        assertEquals(1, parsed.skipped)
    }

    @Test fun `unsafe record text cannot become dictionary syntax`() {
        val parsed = ScelDictionary.parse(scelFixture(listOf("你好", "坏\tni", "坏\npatch:", "#comment")))
        assertEquals(listOf("你好"), parsed.entries.map { it.word })
        assertEquals(3, parsed.skipped)
    }

    @Test fun `bounded input refuses oversized downloads`() {
        val oversized = object : java.io.InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill(0, off, off + len); return len }
        }
        assertThrows(IllegalArgumentException::class.java) { ScelDictionary.readBounded(oversized) }
    }

    /** Optional integration corpus: downloaded once from the public catalog, never fetched by tests. */
    @Test fun `downloaded official corpus converts with the production parser`() {
        val directory = sequenceOf(File(".codex-artifacts/sogou-validation"), File("../.codex-artifacts/sogou-validation"))
            .firstOrNull { it.isDirectory }
        assumeTrue("Local verification corpus unavailable", directory != null)
        val rows = CellDictionaryCatalog.offers.map { offer ->
            val file = File(requireNotNull(directory), "${offer.sourceId}.scel")
            assertTrue("Missing ${offer.name}", file.isFile)
            val result = ScelDictionary.parse(file.readBytes())
            assertTrue(result.entries.isNotEmpty())
            // Round-trip conversion is the same path used before installing a downloaded source.
            val restored = PersonalDictManager.parsePersonalDictEntries(ScelDictionary.toRime("cell_${offer.id}", result.entries))
            assertEquals(offer.name, result.entries, restored)
            "${offer.sourceId}\t${offer.name}\t${result.entries.size}\t${result.skipped}"
        }
        File(directory, "parsed-counts.tsv").writeText(rows.joinToString("\n") + "\n")
        assertEquals(37, rows.size)
    }
}
