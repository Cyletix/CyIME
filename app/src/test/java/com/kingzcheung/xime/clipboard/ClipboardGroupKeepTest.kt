package com.kingzcheung.xime.clipboard

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 放进自定义分组的记录（用户 11-02「他们说不想固定，因为固定就会排到最前面」）：不固定，但和固定的一样
 * 不会被清空、挤掉或过期删除，面板总能读到。以前因为放进分组才固定的，升级后取消固定一次，带编码的不动。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardGroupKeepTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()

    @After fun close() = database.close()

    private suspend fun all() = dao.observeAll().first()
    private suspend fun byText(text: String) = all().single { it.text == text }

    @Test fun groupedRecordsSurviveClearingAndAreNotCountedAsHistory() = runBlocking {
        dao.upsert("常用的", 100)
        dao.upsert("随手复制", 200)
        dao.syncGrouped(listOf(byText("常用的").id))
        assertEquals(1, dao.countUnpinned())
        dao.clearUnpinned()
        assertEquals(listOf("常用的"), all().map { it.text })
        assertFalse(all().single().isPinned)
        dao.clearAllClipboard()
        assertEquals(1, all().size)
    }

    @Test fun groupedRecordsAreAlwaysLoadedBeyondTheHistoryPage() = runBlocking {
        dao.upsert("很早放进分组的", 1)
        (2L..10L).forEach { dao.upsert("历史 $it", it) }
        dao.syncGrouped(listOf(byText("很早放进分组的").id))
        val window = dao.observeWindow(3).first().map { it.text }
        assertEquals(listOf("历史 10", "历史 9", "历史 8", "很早放进分组的"), window)
    }

    @Test fun syncFollowsTheGroupsExactly() = runBlocking {
        dao.upsert("甲", 100); dao.upsert("乙", 200)
        val a = byText("甲").id; val b = byText("乙").id
        dao.syncGrouped(listOf(a, b))
        assertTrue(all().all { it.grouped })
        dao.syncGrouped(listOf(b))
        assertFalse(byText("甲").grouped)
        assertTrue(byText("乙").grouped)
        assertEquals(listOf(b), dao.existingIds(listOf(b, 999)))
    }

    @Test fun groupedPasswordsAndReceivedCopiesAreKept() = runBlocking {
        dao.upsert("密码", 100, sensitive = true)
        dao.addReceived("别的设备的", 100, "", maxCount = 100, maxBytes = 1_000_000)
        dao.syncGrouped(all().map { it.id })
        assertEquals(0, dao.deleteSensitiveBefore(Long.MAX_VALUE))
        assertEquals(0, dao.countReceived())
        dao.addReceived("新的", 200, "", maxCount = 0, maxBytes = 1_000_000)
        assertEquals(setOf("密码", "别的设备的"), all().mapTo(mutableSetOf()) { it.text })
    }

    @Test fun addingToAGroupDoesNotFixTheRecord() = runBlocking {
        val id = dao.addToGroup("新加的常用语", "", 100)
        val added = all().single()
        assertEquals(id, added.id)
        assertTrue(added.grouped)
        assertFalse(added.isPinned)
        // Text already in history is used in place, not added twice.
        dao.upsert("复制过的", 200)
        assertEquals(byText("复制过的").id, dao.addToGroup("复制过的\n", "", 300))
        assertEquals(2, all().size)
    }

    @Test fun recordsFixedOnlyByTheirGroupAreUnfixedOnce() = runBlocking {
        dao.insert(ClipboardEntry(text = "放进分组时固定的", timestamp = 100, isPinned = true))
        dao.insert(ClipboardEntry(text = "带编码的", code = "dz", timestamp = 100, isPinned = true))
        dao.insert(ClipboardEntry(text = "自己固定的", timestamp = 100, isPinned = true))
        dao.syncGrouped(listOf(byText("放进分组时固定的").id, byText("带编码的").id))
        assertEquals(1, dao.unpinGrouped())
        assertFalse(byText("放进分组时固定的").isPinned)
        assertTrue(byText("带编码的").isPinned)
        assertTrue(byText("自己固定的").isPinned)
    }

    @Test fun duplicateCleanupKeepsGroupedRecords() = runBlocking {
        dao.insert(ClipboardEntry(text = "地址", timestamp = 100))
        dao.insert(ClipboardEntry(text = "地址 ", timestamp = 200))
        dao.syncGrouped(listOf(all().minBy { it.timestamp }.id))
        dao.deleteDuplicateHistory()
        val left = all().single()
        assertEquals(100L, left.timestamp)
        assertTrue(left.grouped)
    }
}
