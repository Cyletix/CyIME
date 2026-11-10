package com.kingzcheung.xime.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnglishBigramMemoryTest {
    @get:Rule val tempDir = TemporaryFolder()

    private fun dir() = tempDir.root

    @After fun release() = EnglishBigramMemory.release()

    @Test fun remembersPairsAndRanksByCountThenRecency() {
        val dir = dir()
        EnglishBigramMemory.remember(dir, "thank", "you")
        EnglishBigramMemory.remember(dir, "thank", "god")
        EnglishBigramMemory.remember(dir, "thank", "you")
        assertEquals(listOf("you", "god"), EnglishBigramMemory.next(dir, "thank", 5))
    }

    @Test fun lookupIgnoresCaseAndApostrophes() {
        val dir = dir()
        EnglishBigramMemory.remember(dir, "I'm", "fine")
        EnglishBigramMemory.remember(dir, "Don't", "worry")
        assertEquals(listOf("fine"), EnglishBigramMemory.next(dir, "im", 5))
        assertEquals(listOf("worry"), EnglishBigramMemory.next(dir, "dont", 5))
        assertEquals(listOf("fine"), EnglishBigramMemory.next(dir, "I'm", 5))
    }

    @Test fun rejectsNonWordsAndSelfPairs() {
        val dir = dir()
        EnglishBigramMemory.remember(dir, "a", "123")
        EnglishBigramMemory.remember(dir, "a", "b c")
        EnglishBigramMemory.remember(dir, "same", "same")
        assertEquals(emptyList<String>(), EnglishBigramMemory.next(dir, "a", 5))
        assertEquals(emptyList<String>(), EnglishBigramMemory.next(dir, "same", 5))
    }

    @Test fun keepsAtMostEightPerPreviousWord() {
        val dir = dir()
        for (index in 0 until 12) {
            val next = "b" + ('a' + index)
            EnglishBigramMemory.remember(dir, "a", next)
        }
        assertTrue(EnglishBigramMemory.next(dir, "a", 20).size <= EnglishBigramMemory.MAX_NEXT_PER_PREVIOUS)
    }

    @Test fun aggregateFileIsReadBackAndCompacted() {
        val dir = dir()
        val file = File(dir, EnglishBigramMemory.FILE_NAME)
        file.writeText("# header\n3\tmy\tname\n1\tmy\temail\n")
        assertEquals(listOf("name", "email"), EnglishBigramMemory.next(dir, "my", 5))
        EnglishBigramMemory.remember(dir, "my", "name")
        assertTrue(file.readText().contains("my\tname"))
        assertEquals(listOf("name", "email"), EnglishBigramMemory.next(dir, "my", 5))
    }

    @Test fun backupIncludesThePairFile() {
        assertTrue(BackupManager.isRimePersonalFile(EnglishBigramMemory.FILE_NAME))
        assertTrue(BackupManager.isRimePersonalFile(EnglishPhraseMemory.FILE_NAME))
    }
}
