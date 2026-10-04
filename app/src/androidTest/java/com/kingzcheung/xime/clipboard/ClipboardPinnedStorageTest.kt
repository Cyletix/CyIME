package com.kingzcheung.xime.clipboard

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Uses the production Room DAO; no real clipboard or application database is changed. */
class ClipboardPinnedStorageTest {
    private lateinit var database: ClipboardDatabase

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun legacyRowsKeepTheirIdsTextCodesAndTimestampsWithoutDeduplication() = runBlocking {
        val dao = database.clipboardDao()
        val original = listOf(
            ClipboardEntry(10, "相同文字", "a", 100, isPinned = false, isQuickSend = true, consumed = true),
            ClipboardEntry(11, "相同文字", "b", 200, isPinned = true, isQuickSend = true),
            ClipboardEntry(12, "相同文字", "", 300),
        )
        dao.insertAll(original)
        dao.migrateQuickSendToPinned()
        assertEquals(original.map { if (it.isQuickSend) it.copy(isPinned = true, isQuickSend = false) else it }
            .sortedByDescending { it.timestamp }, dao.observeAll().first())
        assertEquals(setOf(10L, 11L), dao.observeQuickSend().first().map { it.id }.toSet())

        // A later startup must not re-pin something the user has subsequently unfixed.
        dao.setClipboardPinned(10, false)
        val afterUnpin = dao.observeAll().first()
        dao.migrateQuickSendToPinned()
        assertEquals(afterUnpin, dao.observeAll().first())
        assertEquals(listOf(11L), dao.observeQuickSend().first().map { it.id })
        assertEquals("a", requireNotNull(dao.findById(10)).code)
    }

    @Test fun oneHundredFixedRecordsSurviveHistoryTrimmingAndClearing() = runBlocking {
        val dao = database.clipboardDao()
        repeat(100) { dao.insertPinnedText("固定 $it", "code$it", it.toLong()) }
        repeat(10) { dao.upsertAndTrim("最近 $it", 1000L + it, 3) }
        assertEquals(103, dao.observeAll().first().size)
        assertEquals(100, dao.observeQuickSend().first().size)
        assertEquals(listOf("最近 9", "最近 8", "最近 7"),
            dao.observeAll().first().filterNot { it.isPinned }.map { it.text })
        dao.clearAllClipboard()
        assertEquals(100, dao.observeAll().first().size)
        assertTrue(dao.observeAll().first().all { it.isPinned && it.code.isNotBlank() })

        val deleted = dao.observeAll().first().first().id
        dao.deleteClipboardByIds(listOf(deleted))
        assertNull(dao.findById(deleted))
        assertEquals(99, dao.observeQuickSend().first().size)
    }

    @Test fun editingPreservesIdentityAndPinStateAndOnlyChangesCodeWhenSupplied() = runBlocking {
        val dao = database.clipboardDao()
        val fixed = dao.insertPinnedText("旧文字", "trigger", 100)
        val ordinary = dao.insert(ClipboardEntry(text = "普通历史", code = "kept", timestamp = 200))
        dao.updateClipboardItem(fixed, "编辑固定", null, 300)
        val edited = requireNotNull(dao.findById(fixed))
        assertEquals("编辑固定", edited.text)
        assertEquals("trigger", edited.code)
        assertTrue(edited.isPinned)
        assertEquals(300L, edited.timestamp)
        dao.updateClipboardItem(ordinary, "编辑历史", null, 400)
        assertFalse(requireNotNull(dao.findById(ordinary)).isPinned)
        assertEquals("kept", requireNotNull(dao.findById(ordinary)).code)
        dao.updateClipboardItem(fixed, "清除编码", "", 500)
        assertEquals("", requireNotNull(dao.findById(fixed)).code)
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test fun copyingFixedTextAgainRetainsCodeAndIdentity() = runBlocking {
        val dao = database.clipboardDao()
        val fixed = dao.insertPinnedText("反复复制", "copy", 100)
        dao.markConsumed(fixed)
        dao.upsertAndTrim("反复复制", 200, 1)
        val copied = requireNotNull(dao.findById(fixed))
        assertEquals(1, dao.observeAll().first().size)
        assertEquals("copy", copied.code)
        assertTrue(copied.isPinned)
        assertFalse(copied.consumed)
        assertEquals(200L, copied.timestamp)
    }

    @Test fun versionThreeUpgradeNormalizesFlagsInPlaceAndCanBeRepeated() = runBlocking {
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.prepare("""CREATE TABLE clipboard_entries (
                id INTEGER PRIMARY KEY, text TEXT NOT NULL, code TEXT NOT NULL,
                timestamp INTEGER NOT NULL, isPinned INTEGER NOT NULL,
                isQuickSend INTEGER NOT NULL, consumed INTEGER NOT NULL)""").use { it.step() }
            connection.prepare("INSERT INTO clipboard_entries VALUES (17, '同一段', 'abc', 42, 0, 1, 1)").use { it.step() }
            connection.prepare("INSERT INTO clipboard_entries VALUES (18, '同一段', 'xyz', 43, 1, 1, 0)").use { it.step() }
            repeat(2) { ClipboardDatabase.MIGRATION_3_4.migrate(connection) }
            connection.prepare("SELECT id, text, code, timestamp, isPinned, isQuickSend, consumed FROM clipboard_entries ORDER BY id").use {
                assertTrue(it.step())
                assertEquals(17L, it.getLong(0))
                assertEquals("同一段", it.getText(1))
                assertEquals("abc", it.getText(2))
                assertEquals(42L, it.getLong(3))
                assertEquals(1L, it.getLong(4))
                assertEquals(0L, it.getLong(5))
                assertEquals(1L, it.getLong(6))
                assertTrue(it.step())
                assertEquals(18L, it.getLong(0))
                assertEquals("xyz", it.getText(2))
                assertEquals(43L, it.getLong(3))
                assertEquals(1L, it.getLong(4))
                assertEquals(0L, it.getLong(5))
                assertFalse(it.step())
            }
        }
    }
}
