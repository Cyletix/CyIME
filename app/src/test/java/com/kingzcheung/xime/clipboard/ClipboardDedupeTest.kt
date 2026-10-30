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

/** 剪贴板去重（用户 10-30）：同一段文字只留一条，首尾空白不算区别；固定内容和它的编码不丢。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardDedupeTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()

    @After fun close() = database.close()

    private suspend fun all() = dao.observeAll().first()

    @Test fun copyingTheSameTextWithDifferentEndsKeepsOneRecordWithTheNewestText() = runBlocking {
        assertTrue(dao.upsert("送到前台", 100))
        assertFalse(dao.upsert("送到前台\n", 200))
        assertFalse(dao.upsert("　送到前台 ", 300))
        val records = all()
        assertEquals(1, records.size)
        assertEquals("　送到前台 ", records.single().text)
        assertEquals(300L, records.single().timestamp)
        // Different text inside still counts as different.
        assertTrue(dao.upsert("送到 前台", 400))
        assertEquals(2, all().size)
    }

    @Test fun copyingFixedTextKeepsTheFixedRecordAndItsText() = runBlocking {
        val fixed = dao.insertPinnedText("我的地址", "dz", 100)
        assertFalse(dao.upsert("我的地址\n", 200))
        val records = all()
        assertEquals(1, records.size)
        assertEquals(fixed, records.single().id)
        assertEquals("我的地址", records.single().text)
        assertEquals("dz", records.single().code)
        assertEquals(200L, records.single().timestamp)
    }

    @Test fun fixingTextFromHistoryPinsThatRecordInsteadOfAddingOne() = runBlocking {
        dao.upsert("会议室 302", 100)
        val history = all().single().id
        assertEquals(history, dao.addPinned("会议室 302", "", 200))
        val records = all()
        assertEquals(1, records.size)
        assertTrue(records.single().isPinned)
        // The same fixed text and code again only moves up.
        assertEquals(history, dao.addPinned("会议室 302", "", 300))
        assertEquals(1, all().size)
        // A different trigger code on equal text is a separate fixed record, as before.
        dao.addPinned("会议室 302", "hys", 400)
        assertEquals(2, all().size)
    }

    @Test fun existingDuplicatesAreRemovedOnceKeepingTheNewestAndEveryFixedRecord() = runBlocking {
        dao.insert(ClipboardEntry(text = "旧的重复", timestamp = 100))
        dao.insert(ClipboardEntry(text = "旧的重复 ", timestamp = 300))
        dao.insert(ClipboardEntry(text = "旧的重复", timestamp = 200))
        dao.insert(ClipboardEntry(text = "固定过的", timestamp = 50, isPinned = true, code = "a"))
        dao.insert(ClipboardEntry(text = "固定过的", timestamp = 60, isPinned = true, code = "b"))
        dao.insert(ClipboardEntry(text = "固定过的\n", timestamp = 500))
        dao.insert(ClipboardEntry(text = "只有一条", timestamp = 400))
        assertEquals(3, dao.deleteDuplicateHistory())
        val records = all()
        assertEquals(listOf("只有一条", "旧的重复 ", "固定过的", "固定过的"), records.map { it.text })
        assertEquals(setOf("a", "b"), records.filter { it.isPinned }.map { it.code }.toSet())
        assertEquals(0, dao.deleteDuplicateHistory())
    }

    @Test fun editingIntoExistingTextLeavesOneRecord() = runBlocking {
        dao.upsert("甲", 100)
        dao.upsert("乙", 200)
        val edited = all().first { it.text == "乙" }.id
        dao.updateClipboardItem(edited, "甲", null, 300)
        dao.deleteHistoryCopies("甲", edited)
        assertEquals(listOf(edited), all().map { it.id })
    }
}
