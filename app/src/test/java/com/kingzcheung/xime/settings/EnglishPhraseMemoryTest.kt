package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnglishPhraseMemoryTest {
    @get:Rule val tempDir = TemporaryFolder()

    private fun dir() = tempDir.root

    @Test fun learnsAddressesAndDomainsButNotOrdinaryWords() {
        val dir = dir()
        EnglishPhraseMemory.remember(dir, "zhang.san@example.com")
        EnglishPhraseMemory.remember(dir, "example.org")
        EnglishPhraseMemory.remember(dir, "hello")
        EnglishPhraseMemory.remember(dir, "12345")
        assertEquals(
            listOf("zhang.san@example.com", "example.org"),
            java.io.File(dir, EnglishPhraseMemory.FILE_NAME).readLines(),
        )
    }

    @Test fun duplicateSpellingsAreCompacted() {
        assertEquals(
            listOf("c@d.com", "A@b.com"),
            EnglishPhraseMemory.compact(listOf("a@b.com", "c@d.com", "A@b.com", "hello")),
        )
    }

    @Test fun backupIncludesThePhraseFile() {
        assertTrue(BackupManager.isRimePersonalFile(EnglishPhraseMemory.FILE_NAME))
    }
}
