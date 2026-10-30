package com.kingzcheung.xime.clipboard

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
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

/** 别的设备同步来的记录另算容量（用户 10-30）：只挤掉更早的同步记录，不挤本机复制和固定内容。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardReceivedRoomTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()
    private val count = ClipboardCapacity.DEVICE_COUNT
    private val bytes = ClipboardCapacity.DEVICE_BYTES

    @After fun close() = database.close()

    private suspend fun all() = dao.observeAll().first()

    @Test fun receivedCopiesPushOutOnlyOlderReceivedCopies() = runBlocking {
        repeat(50) { dao.upsert("手机复制 $it", it.toLong()) }
        dao.insertPinnedText("我的地址", "dz", 0)
        repeat(250) { dao.addReceived("电脑来的 $it", 1_000L + it, "", count, bytes) }
        val records = all()
        assertEquals(50, records.count { it.text.startsWith("手机复制") })
        assertTrue(records.any { it.isPinned && it.text == "我的地址" })
        assertEquals(count, records.count { it.fromDevice })
        assertFalse(records.any { it.text == "电脑来的 149" })
        assertTrue(records.any { it.text == "电脑来的 150" })
    }

    @Test fun longReceivedCopiesAreCappedBySizeAlone() = runBlocking {
        dao.upsert("手机复制", 1)
        val long = "长".repeat(20_000)
        repeat(40) { dao.addReceived(long + it, 10L + it, "", count, bytes) }
        assertTrue(dao.receivedBytes() <= bytes)
        assertTrue(dao.countReceived() in 1 until 40)
        assertTrue(all().any { it.text == "手机复制" && !it.fromDevice })
    }

    @Test fun receivedRecordsDoNotFillThisPhonesRoom() = runBlocking {
        dao.upsert("手机复制", 1)
        val own = dao.observeStoredBytes().first()
        dao.addReceived("电脑来的一段很长的文字", 2, "", count, bytes)
        assertEquals(own, dao.observeStoredBytes().first())
        // A fixed received record is kept like any fixed record and counts as this phone's.
        val received = all().single { it.fromDevice }
        dao.setClipboardPinned(received.id, true)
        assertTrue(dao.observeStoredBytes().first() > own)
        repeat(count + 10) { dao.addReceived("又来 $it", 10L + it, "", count, bytes) }
        assertTrue(all().any { it.id == received.id && it.isPinned })
    }

    @Test fun copyingReceivedTextHereMakesItThisPhonesOwn() = runBlocking {
        dao.addReceived("会议室 302", 1, "", count, bytes)
        dao.upsert("会议室 302", 2)
        assertFalse(all().single().fromDevice)
        // Text already copied here stays this phone's own when it also arrives from another device.
        dao.addReceived("会议室 302", 3, "", count, bytes)
        val record = all().single()
        assertFalse(record.fromDevice)
        assertEquals(3L, record.timestamp)
    }
}
