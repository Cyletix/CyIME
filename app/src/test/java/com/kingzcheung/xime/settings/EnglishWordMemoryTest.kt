package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnglishWordMemoryTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun words(dir: File) = File(dir, EnglishWordMemory.FILE_NAME).readLines()

    @Test
    fun remembersWordsAsTyped() {
        val dir = tempDir.root
        EnglishWordMemory.remember(dir, "Obsidian")
        EnglishWordMemory.remember(dir, "Obsidian")
        EnglishWordMemory.remember(dir, "transformer")
        assertEquals(listOf("Obsidian", "transformer"), words(dir))
    }

    @Test
    fun skipsShortLowercaseAndNonLetters() {
        val dir = tempDir.root
        listOf("abc", "nh", "a", "hello world", "C++", "123", "zhong'guo").forEach { EnglishWordMemory.remember(dir, it) }
        EnglishWordMemory.remember(dir, "KFC")
        assertEquals(listOf("KFC"), words(dir))
    }

    @Test
    fun forgetRemovesEverySpelling() {
        val dir = tempDir.root
        listOf("Obsidian", "Notion", "OBSIDIAN").forEach { EnglishWordMemory.remember(dir, it) }
        assertTrue(EnglishWordMemory.forget(dir, "Obsidian"))
        assertEquals(listOf("Notion"), words(dir))
        assertFalse(EnglishWordMemory.forget(dir, "Obsidian"))
        assertFalse(EnglishWordMemory.forget(dir, "你好"))
    }

    @Test
    fun compactKeepsLatestOrder() {
        assertEquals(listOf("Notion", "Obsidian"), EnglishWordMemory.compact(listOf("Obsidian", "Notion", "Obsidian", "x y")))
    }

    @Test
    fun backupIncludesTheWordFile() {
        assertTrue(BackupManager.isRimePersonalFile(EnglishWordMemory.FILE_NAME))
    }
}
