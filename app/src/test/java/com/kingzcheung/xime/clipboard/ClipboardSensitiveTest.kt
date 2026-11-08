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

/**
 * 「记录密码等敏感内容」(#95)：开关关着照旧不记；开着时记下但藏起来，不进搜索，1 小时后删除，固定的留着。
 * 无痕和剪贴板搜索开着时照样不记。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardSensitiveTest {
    private val database = Room.inMemoryDatabaseBuilder<ClipboardDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val dao = database.clipboardDao()
    private val hour = ClipboardManager.SENSITIVE_LIFETIME_MS

    @After fun close() = database.close()

    private suspend fun all() = dao.observeAll().first()

    @Test fun switchOffKeepsPasswordsOutAsBefore() {
        assertEquals(ClipPrivacy.PRIVATE, ClipboardManager.clipPrivacy(markedSensitive = true, passwordField = false,
            incognito = false, keepSensitive = false))
        assertEquals(ClipPrivacy.PRIVATE, ClipboardManager.clipPrivacy(markedSensitive = false, passwordField = true,
            incognito = false, keepSensitive = false))
        assertEquals(ClipPrivacy.NORMAL, ClipboardManager.clipPrivacy(markedSensitive = false, passwordField = false,
            incognito = false, keepSensitive = false))
    }

    @Test fun switchOnKeepsPasswordsHiddenButIncognitoStillWins() {
        assertEquals(ClipPrivacy.SECRET, ClipboardManager.clipPrivacy(markedSensitive = true, passwordField = false,
            incognito = false, keepSensitive = true))
        assertEquals(ClipPrivacy.SECRET, ClipboardManager.clipPrivacy(markedSensitive = false, passwordField = true,
            incognito = false, keepSensitive = true))
        assertEquals(ClipPrivacy.PRIVATE, ClipboardManager.clipPrivacy(markedSensitive = true, passwordField = false,
            incognito = true, keepSensitive = true))
        // An ordinary copy stays ordinary with the switch on.
        assertEquals(ClipPrivacy.NORMAL, ClipboardManager.clipPrivacy(markedSensitive = false, passwordField = false,
            incognito = false, keepSensitive = true))
    }

    @Test fun keptPasswordIsHiddenFromSearch() = runBlocking {
        dao.upsert("Abc-123-示例", 1_000L, "", sensitive = true)
        dao.upsert("Abc 普通记录", 2_000L, "abc")
        assertEquals(listOf("Abc 普通记录"), dao.search(ClipboardSearch.likePattern("Abc"), 20).map { it.text })
        assertEquals(1, all().count { it.sensitive })
        assertTrue(dao.missingSearchKeys(10).none { it.text == "Abc-123-示例" })
    }

    @Test fun passwordsGoAfterAnHourButFixedOnesStay() = runBlocking {
        val now = 10 * hour
        dao.upsert("旧密码", now - hour - 1, "", sensitive = true)
        dao.upsert("新密码", now - 60_000L, "", sensitive = true)
        dao.upsert("普通旧记录", 0L, "ptjjl")
        val fixed = dao.insertPinnedText("固定的密码", "", now - 5 * hour)
        dao.setSensitive(fixed, true)
        dao.deleteSensitiveBefore(now - hour)
        assertEquals(setOf("新密码", "普通旧记录", "固定的密码"), all().map { it.text }.toSet())
        // Turning the switch off removes every kept password that is not fixed.
        dao.deleteSensitiveBefore(Long.MAX_VALUE)
        assertEquals(setOf("普通旧记录", "固定的密码"), all().map { it.text }.toSet())
    }

    @Test fun latestCopyDecidesWhetherAnUnfixedRecordIsHidden() = runBlocking {
        dao.upsert("同一段文字", 1_000L, "tydwz")
        dao.upsert("同一段文字", 2_000L, "", sensitive = true)
        all().single().let { assertTrue(it.sensitive); assertEquals("", it.searchKey) }
        dao.upsert("同一段文字", 3_000L, "tydwz")
        assertFalse(all().single().sensitive)
    }

    @Test fun aFixedPasswordStaysHiddenWhenCopiedAgainOrdinarily() = runBlocking {
        val id = dao.insertPinnedText("固定的密码", "", 1_000L)
        dao.setSensitive(id, true)
        dao.upsert("固定的密码", 2_000L, "gddmm")
        assertTrue(all().single().sensitive)
    }

    @Test fun keptPasswordsShowAsDotsOnTheCandidateBarAndPasteTheirText() {
        val items = listOf(ClipboardItem(1, "https://example.com/a?utm_source=x 密码 13812345678", sensitive = true),
            ClipboardItem(2, "https://example.com/b?utm_source=x"))
        val choices = ClipboardLinkChoices.expandItems(items)
        // The password gives only its own text (no clean link, no number); the ordinary link still gets one,
        // in the short capsule in front of the originals.
        assertEquals(listOf(1, 0, 1), choices.map { it.source })
        assertEquals(1, ClipboardLinkChoices.extractedCount(items))
        assertEquals(listOf("https://example.com/b", ClipboardLinkChoices.SENSITIVE_MASK, "https://example.com/b?utm_source=x"),
            choices.map { ClipboardLinkChoices.shownText(items, it) })
        assertEquals("https://example.com/a?utm_source=x 密码 13812345678", choices[1].text)
    }

    @Test fun keptPasswordIsPastedPrivately() {
        val kept = listOf(ClipboardItem(3, "Abc-123-示例", sensitive = true))
        assertEquals(ClipboardPasteMode.PRIVATE, clipboardPasteMode("Abc-123-示例", kept))
    }
}
