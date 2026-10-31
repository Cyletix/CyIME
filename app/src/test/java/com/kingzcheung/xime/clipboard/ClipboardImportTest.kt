package com.kingzcheung.xime.clipboard

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.StringWriter

/** 剪贴板导入（用户 10-31：只能导出没有再导入）：导出的三种文件都能原样读回。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardImportTest {
    private val items = listOf(
        ClipboardItem(2, "我的地址", code = "dz", timestamp = 1_700_000_040_000, isPinned = true),
        ClipboardItem(5, "周报模板\n第二行", timestamp = 1_700_000_030_000, isPinned = true),
        ClipboardItem(3, "新的历史 ```代码```\n\n----", timestamp = 1_700_000_020_000),
        ClipboardItem(1, "旧的历史", timestamp = 1_700_000_010_000),
    )
    private val groups = mapOf(5L to "工作")

    private fun export(format: ClipboardExportFormat) = StringWriter().also {
        ClipboardExport.write(items, format, it, 1_700_000_100_000) { item -> groups[item.id] }
    }.toString()

    @Test fun jsonComesBackWithFixedCodesGroupsAndTimes() {
        val clips = ClipboardImport.parse(export(ClipboardExportFormat.JSON))
        assertEquals(items.map { it.text }, clips.map { it.text })
        assertEquals(listOf(true, true, false, false), clips.map { it.pinned })
        assertEquals("dz", clips[0].code)
        assertEquals(listOf(null, "工作", null, null), clips.map { it.group })
        assertEquals(items.map { it.timestamp }, clips.map { it.timestamp })
    }

    @Test fun markdownComesBackWithFixedCodesGroupsAndMinutes() {
        val clips = ClipboardImport.parse(export(ClipboardExportFormat.MD))
        assertEquals(items.map { it.text }, clips.map { it.text })
        assertEquals(listOf(true, true, false, false), clips.map { it.pinned })
        assertEquals(listOf("dz", "", "", ""), clips.map { it.code })
        assertEquals(listOf(null, "工作", null, null), clips.map { it.group })
        // The file holds minutes only.
        assertEquals(items.map { it.timestamp / 60_000 }, clips.map { it.timestamp / 60_000 })
    }

    @Test fun textComesBackAsHistoryInFileOrder() {
        val clips = ClipboardImport.parse(export(ClipboardExportFormat.TXT))
        assertEquals(items.map { it.text }, clips.map { it.text })
        assertTrue(clips.none { it.pinned || it.group != null || it.timestamp != 0L })
    }

    @Test fun filesSavedOnWindowsAndWithABomStillRead() {
        val txt = "﻿" + export(ClipboardExportFormat.TXT).replace("\n", "\r\n")
        assertEquals(items.map { it.text }, ClipboardImport.parse(txt).map { it.text })
        val md = export(ClipboardExportFormat.MD).replace("\n", "\r\n")
        assertEquals(items.map { it.text }, ClipboardImport.parse(md).map { it.text })
    }

    @Test fun oneRecordTextFileIsOneRecordAndOtherJsonHasNone() {
        assertEquals(listOf("第一行\n第二行"), ClipboardImport.parse("第一行\n第二行").map { it.text })
        // A text record that only starts with a brace is still text.
        assertEquals(listOf("{不是 JSON"), ClipboardImport.parse("{不是 JSON").map { it.text })
        assertTrue(ClipboardImport.parse("{\"name\": \"别的文件\"}").isEmpty())
        assertTrue(ClipboardImport.parse("").isEmpty())
    }

    @Test fun messageSaysWhatHappened() {
        assertEquals("已导入 3 条（其中固定 1 条），2 条已经有了，没再加", ClipboardImportResult(3, 1, 2, 0).message)
        assertEquals("已导入 0 条，还有 4 条剪贴板放不下，没有导入", ClipboardImportResult(0, 0, 0, 4).message)
        assertEquals("文件里没有能导入的记录", ClipboardImportResult(0, 0, 0, 0).message)
    }
}

/** 导入写进数据库：已有的不重复，历史里的固定内容就地固定，放不下的不写。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardImportRecordTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()

    @After fun close() = database.close()

    private suspend fun all() = dao.observeAll().first()

    @Test fun newRecordsKeepTheirOwnTimeAndFixedState() = runBlocking {
        assertEquals(ClipboardImportRow.ADDED, dao.importRecord("我的地址", "dz", 50, true, "", true).outcome)
        assertEquals(ClipboardImportRow.ADDED, dao.importRecord("旧的历史", "x", 30, false, "", true).outcome)
        val records = all().associateBy { it.text }
        assertTrue(records.getValue("我的地址").isPinned)
        assertEquals("dz", records.getValue("我的地址").code)
        assertEquals(50L, records.getValue("我的地址").timestamp)
        assertEquals(false, records.getValue("旧的历史").isPinned)
        assertEquals("", records.getValue("旧的历史").code)
        assertEquals(30L, records.getValue("旧的历史").timestamp)
    }

    @Test fun importingTheSameFileTwiceAddsNothing() = runBlocking {
        dao.importRecord("我的地址", "dz", 50, true, "", true)
        dao.importRecord("旧的历史", "", 30, false, "", true)
        assertEquals(ClipboardImportRow.EXISTING, dao.importRecord("我的地址", "dz", 50, true, "", true).outcome)
        assertEquals(ClipboardImportRow.EXISTING, dao.importRecord("旧的历史\n", "", 30, false, "", true).outcome)
        assertEquals(2, all().size)
    }

    @Test fun historyCopyOfAFixedRecordIsFixedInPlaceAndKeepsItsTime() = runBlocking {
        dao.upsert("会议室 302", 900)
        val id = all().single().id
        val row = dao.importRecord("会议室 302", "hys", 100, true, "", true)
        assertEquals(ClipboardImportRow.EXISTING, row.outcome)
        assertEquals(id, row.id)
        val record = all().single()
        assertTrue(record.isPinned)
        assertEquals("hys", record.code)
        assertEquals(900L, record.timestamp)
    }

    @Test fun fixedRecordIsNotUnfixedByAHistoryLineAndFullClipboardWritesNothing() = runBlocking {
        dao.insertPinnedText("我的地址", "dz", 100)
        assertEquals(ClipboardImportRow.EXISTING, dao.importRecord("我的地址", "", 10, false, "", true).outcome)
        assertTrue(all().single().isPinned)
        val row = dao.importRecord("新的一条", "", 10, false, "", false)
        assertEquals(ClipboardImportRow.NO_ROOM, row.outcome)
        assertEquals(1, all().size)
        assertNull(all().firstOrNull { it.text == "新的一条" })
    }
}
