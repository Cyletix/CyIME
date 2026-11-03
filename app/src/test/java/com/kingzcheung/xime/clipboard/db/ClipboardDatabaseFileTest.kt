package com.kingzcheung.xime.clipboard.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 剪贴板库换文件名：旧版装回来时不再碰到它打不开的 v8 库（11-03 降级后输入法打不开）。 */
class ClipboardDatabaseFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun dir() = temp.newFolder("databases")

    @Test
    fun `moves the old database with its log files`() {
        val dir = dir()
        File(dir, "clipboard.db").writeText("main")
        File(dir, "clipboard.db-wal").writeText("wal")
        File(dir, "clipboard.db-shm").writeText("shm")
        val name = ClipboardDatabase.moveLegacyDatabase(File(dir, "clipboard.db"), File(dir, "clipboard_history.db"))
        assertEquals("clipboard_history.db", name)
        assertFalse(File(dir, "clipboard.db").exists())
        assertFalse(File(dir, "clipboard.db-wal").exists())
        assertEquals("main", File(dir, "clipboard_history.db").readText())
        assertEquals("wal", File(dir, "clipboard_history.db-wal").readText())
        assertEquals("shm", File(dir, "clipboard_history.db-shm").readText())
    }

    @Test
    fun `keeps a database an older build made after the move`() {
        val dir = dir()
        File(dir, "clipboard_history.db").writeText("records")
        File(dir, "clipboard.db").writeText("older build")
        val name = ClipboardDatabase.moveLegacyDatabase(File(dir, "clipboard.db"), File(dir, "clipboard_history.db"))
        assertEquals("clipboard_history.db", name)
        assertEquals("records", File(dir, "clipboard_history.db").readText())
        assertEquals("older build", File(dir, "clipboard.db").readText())
    }

    @Test
    fun `fresh install opens the new name`() {
        val dir = dir()
        val name = ClipboardDatabase.moveLegacyDatabase(File(dir, "clipboard.db"), File(dir, "clipboard_history.db"))
        assertEquals("clipboard_history.db", name)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `finishes a move that was cut off after the log file`() {
        val dir = dir()
        File(dir, "clipboard_history.db-wal").writeText("wal")
        File(dir, "clipboard.db").writeText("main")
        val name = ClipboardDatabase.moveLegacyDatabase(File(dir, "clipboard.db"), File(dir, "clipboard_history.db"))
        assertEquals("clipboard_history.db", name)
        assertEquals("main", File(dir, "clipboard_history.db").readText())
        assertEquals("wal", File(dir, "clipboard_history.db-wal").readText())
    }
}
