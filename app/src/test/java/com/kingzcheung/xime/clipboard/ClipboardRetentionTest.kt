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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 剪贴板历史的「数量上限」「保留时间」(用户 11-03)：超出的从最早的删，固定的和分组的不计入也不删；
 * 第一次按已有条数定上限，升级不删东西；改小前能先算出要删几条。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardRetentionTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()

    @After fun close() = database.close()

    private suspend fun texts() = dao.observeAll().first().map { it.text }

    @Test fun countLimitDropsTheOldestHistoryOnly() = runBlocking {
        dao.addPinned("固定的", "", 1)
        dao.upsert("分组的", 2)
        dao.syncGrouped(listOf(dao.findSameText("分组的")!!.id))
        (10L..15L).forEach { dao.upsert("历史 $it", it) }
        assertEquals(3, dao.trimHistory(maxCount = 3, cutoff = Long.MIN_VALUE))
        assertEquals(listOf("历史 15", "历史 14", "历史 13", "分组的", "固定的"), texts())
    }

    @Test fun unlimitedAndNeverExpiringKeepEverything() = runBlocking {
        (1L..5L).forEach { dao.upsert("历史 $it", it) }
        val limits = ClipboardHistoryLimits(ClipboardRetention.UNLIMITED, ClipboardRetention.NEVER)
        assertEquals(0, dao.trimHistory(limits.maxCount, limits.cutoff(now = 10)))
        assertEquals(5, texts().size)
    }

    @Test fun keepDaysDropsOldCopiesButNotFixedOrGrouped() = runBlocking {
        val day = ClipboardRetention.DAY_MS
        val now = 100 * day
        dao.addPinned("很早固定的", "", 1)
        dao.upsert("很早分组的", 2)
        dao.syncGrouped(listOf(dao.findSameText("很早分组的")!!.id))
        dao.upsert("8 天前", now - 8 * day)
        dao.upsert("6 天前", now - 6 * day)
        dao.upsert("密码", now - 9 * day, sensitive = true)
        val week = ClipboardHistoryLimits(ClipboardRetention.UNLIMITED, 7)
        assertEquals(2, dao.countHistoryBefore(week.cutoff(now)))
        assertEquals(2, dao.trimHistory(week.maxCount, week.cutoff(now)))
        assertEquals(setOf("很早固定的", "很早分组的", "6 天前"), texts().toSet())
    }

    @Test fun receivedCopiesCountAsHistory() = runBlocking {
        dao.upsert("本机", 1)
        dao.addReceived("别的设备的", 2, "", maxCount = 100, maxBytes = 1_000_000)
        dao.upsert("本机新的", 3)
        dao.trimHistory(maxCount = 2, cutoff = Long.MIN_VALUE)
        assertEquals(listOf("本机新的", "别的设备的"), texts())
    }

    @Test fun firstLimitKeepsWhatIsAlreadyThere() {
        assertEquals(1000, ClipboardRetention.initialCount(0))
        assertEquals(1000, ClipboardRetention.initialCount(1000))
        assertEquals(5000, ClipboardRetention.initialCount(1001))
        assertEquals(ClipboardRetention.UNLIMITED, ClipboardRetention.initialCount(5001))
        assertEquals(ClipboardRetention.DEFAULT_COUNT, ClipboardRetention.initialCount(12))
    }

    @Test fun removedCountIsTheLargerOfBothLimits() {
        assertEquals(0, ClipboardRetention.removedBy(history = 50, expired = 0, maxCount = 100))
        assertEquals(20, ClipboardRetention.removedBy(history = 120, expired = 5, maxCount = 100))
        assertEquals(30, ClipboardRetention.removedBy(history = 120, expired = 30, maxCount = 100))
        assertEquals(30, ClipboardRetention.removedBy(history = 120, expired = 30, maxCount = ClipboardRetention.UNLIMITED))
    }

    @Test fun labelsMatchWindows() {
        assertEquals(listOf("100 条", "300 条", "1000 条", "5000 条", "不限"),
            ClipboardRetention.COUNT_OPTIONS.map(ClipboardRetention::countLabel))
        assertEquals(listOf("1 天", "7 天", "30 天", "90 天", "不过期"),
            ClipboardRetention.DAY_OPTIONS.map(ClipboardRetention::daysLabel))
    }
}
