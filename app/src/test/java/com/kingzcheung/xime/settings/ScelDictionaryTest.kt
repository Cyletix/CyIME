package com.kingzcheung.xime.settings

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

internal fun scelFixture(words: List<String> = listOf("你好", "拟好"), variant: Int = 0x44): ByteArray {
    val wordStart = if (variant == 0x45) 0x26c4 else 0x2628
    val header = ByteArray(wordStart)
    byteArrayOf(0x40, 0x15, 0, 0, variant.toByte(), 0x43, 0x53, 1).copyInto(header)
    "测试词库".toByteArray(Charsets.UTF_16LE).copyInto(header, 0x130)
    var pos = 0x1544
    for ((id, text) in listOf("ni", "hao").withIndex()) {
        header[pos] = id.toByte(); header[pos + 2] = (text.length * 2).toByte()
        text.toByteArray(Charsets.UTF_16LE).copyInto(header, pos + 4)
        pos += 4 + text.length * 2
    }
    return ByteArrayOutputStream().apply {
        write(header)
        fun u16(value: Int) { write(value and 255); write(value shr 8) }
        u16(words.size); u16(4); u16(0); u16(1)
        words.forEach { word ->
            val bytes = word.toByteArray(Charsets.UTF_16LE)
            u16(bytes.size); write(bytes); u16(10); write(ByteArray(10))
        }
    }.toByteArray()
}

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

    @Test fun `rejects html unsupported versions and truncated records`() {
        for (bytes in listOf("<html>access denied</html>".toByteArray(), scelFixture().dropLast(1).toByteArray(),
            scelFixture().also { it[4] = 0x46 }, scelFixture().also { it[0x262e] = 99 })) {
            assertThrows(Exception::class.java) { ScelDictionary.parse(bytes) }
        }
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
        assertEquals(24, rows.size)
    }
}
