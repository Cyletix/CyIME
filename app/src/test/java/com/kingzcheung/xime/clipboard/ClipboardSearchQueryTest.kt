package com.kingzcheung.xime.clipboard

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The production search queries on an in-memory database with made-up records. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardSearchQueryTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()

    @After fun close() = database.close()

    private fun add(text: String, key: String, time: Long, pinned: Boolean = false, quick: Boolean = false) =
        runBlocking { dao.insert(ClipboardEntry(text = text, searchKey = key, timestamp = time, isPinned = pinned, isQuickSend = quick)) }

    private fun texts(found: List<ClipboardEntry>) = found.map { it.text }

    @Test fun initialsMatchAnywhereFixedFirstThenNewest() = runBlocking {
        add("送货地址改成前台", "shdzgcqt", 100)
        add("地址确认后下单", "dzqrhxd", 300)
        add("我的地址", "wddz", 50, pinned = true)
        add("东京", "dj", 400)
        add("旧的快捷发送地址", "jdkjfsdz", 500, quick = true)
        assertEquals(listOf("我的地址", "地址确认后下单", "送货地址改成前台"),
            texts(dao.searchKey(ClipboardSearch.keyPattern("dz"), 50)))
    }

    @Test fun keypadDigitsFindEverySpellingOfTheSameKeys() = runBlocking {
        add("地址", "dz", 100)          // d z → 3 9
        add("房子", "fz", 200)          // f z → 3 9
        add("电话", "dh", 300)          // d h → 3 4
        add("房间 39 号", "fj 39 h", 400) // typed digits stay digits
        assertEquals(listOf("房间 39 号", "房子", "地址"),
            texts(dao.searchKeyDigits(ClipboardSearch.keyPattern(ClipboardSearch.t9Digits("dz")), 50)))
    }
}
