package com.kingzcheung.xime.rime

import com.kingzcheung.xime.settings.InputLanguage
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class AutomaticPreparationTest {
    @Test fun failuresRetryInOrderUntilReady() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val messages = mutableListOf<String>()
        retryPreparation(messages::add, { waits.add(it) }) {
            attempts++
            if (attempts == 1) error("故障注入")
            attempts == 3
        }
        assertEquals(3, attempts)
        assertEquals(listOf(3000L, 10000L), waits)
        assertTrue(messages.any { "自动重试" in it })
    }
    @Test fun failedDeploymentShowsItsRecordedReasonUntilRetry() = runBlocking {
        val messages = mutableListOf<String>()
        var tries = 0
        retryPreparation(messages::add, {}, { "部署失败：方案缺少词典索引" }) { ++tries == 2 }
        assertTrue(messages.any { "缺少词典索引" in it && "3 秒后自动重试" in it })
    }
    @Test fun noticeNamesWhatIsBeingPrepared() {
        // 中文早已编译好，只开了德语、法语：不能说「准备中文输入」。
        assertEquals("正在准备德语、法语输入，请稍候…",
            preparingMessage(listOf(InputLanguage.GERMAN, InputLanguage.FRENCH, InputLanguage.GERMAN), firstTime = false))
        assertEquals("正在更新输入方案，请稍候…", preparingMessage(emptyList(), firstTime = false))
        assertEquals("正在更新输入方案，请稍候…", preparingMessage(listOf(InputLanguage.UNSPECIFIED), firstTime = false))
        // 新装或中文方案本身缺编译结果时，确实是在准备中文。
        assertEquals("正在自动准备中文输入，请稍候…", preparingMessage(listOf(InputLanguage.JAPANESE), firstTime = true))
        assertEquals("正在自动准备中文输入，请稍候…",
            preparingMessage(listOf(InputLanguage.CHINESE, InputLanguage.KOREAN), firstTime = false))
    }
    @Test fun retryShowsTheSuppliedNotice() = runBlocking {
        val messages = mutableListOf<String>()
        retryPreparation(messages::add, {}, preparing = { "正在准备日语输入，请稍候…" }) { true }
        assertEquals(listOf("正在准备日语输入，请稍候…"), messages)
    }
    @Test fun unreadablePackageSaysReinstallInsteadOfABlankLine() = runBlocking {
        // GitHub #283：系统读安装包抛出的 IOException 没有说明，原来提示只剩「3 秒后自动重试，无需操作」。
        val assetRead = java.io.IOException("").apply {
            stackTrace = arrayOf(StackTraceElement("android.content.res.AssetManager", "nativeAssetRead", null, -2))
        }
        val messages = mutableListOf<String>()
        var tries = 0
        retryPreparation(messages::add, {}) { if (++tries == 1) throw assetRead else true }
        assertTrue(messages.any { it.startsWith(UNREADABLE_PACKAGE) && "无需操作" !in it })
        // 其它没有说明的异常至少说出是哪一类，不再显示空行。
        assertEquals("准备失败（IOException）", preparationFailureText(java.io.IOException("")))
        assertEquals("部署失败", preparationFailureText(IllegalStateException("部署失败")))
    }
    @Test fun cancellationDoesNotRetryOrPretendReady() = runBlocking {
        var waits = 0
        try {
            retryPreparation({}, { waits++ }) { throw CancellationException("closed") }
            fail("cancel must propagate")
        } catch (_: CancellationException) { assertEquals(0, waits) }
    }
}
