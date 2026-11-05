package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryStatsTest {
    @Test fun readsPssFromSmapsRollup() {
        val rollup = """
            7f0000000000-7fffffffffff ---p 00000000 00:00 0                          [rollup]
            Rss:              412340 kB
            Pss:              301268 kB
            Pss_Anon:         120000 kB
            Shared_Clean:      90000 kB
        """.trimIndent()
        assertEquals(301268L, MemoryStats.parsePssKb(rollup))
    }

    @Test fun missingPssLineIsNull() {
        assertNull(MemoryStats.parsePssKb("Rss: 1 kB"))
        assertNull(MemoryStats.parsePssKb(""))
    }

    @Test fun systemPssLeavesOutSwap() {
        assertEquals(600_000L, MemoryStats.residentPssKb(741_000, 141_000))
        assertNull(MemoryStats.residentPssKb(0, 0))
    }

    @Test fun voiceRowTakesTheNumberTheVoiceProcessReports() {
        val snapshot = MemoryStats.Snapshot(listOf(
            MemoryStats.ProcessUse("p", "键盘与设置", 100L * 1024, isVoice = false),
            MemoryStats.ProcessUse("p:asr", "语音与字幕", 0L, isVoice = true)), 1L, 2L, false, null)
        with(MemoryStats) {
            assertEquals(741_000L * 1024, snapshot.withVoice(741_000).voice!!.pssBytes)
            assertEquals(100L * 1024, snapshot.withVoice(741_000).processes.first().pssBytes)
            assertEquals(snapshot, snapshot.withVoice(null))
        }
    }

    @Test fun processTitlesUsePlainNames() {
        val pkg = "com.kingzcheung.xime"
        assertEquals("键盘与设置", MemoryStats.title(pkg, pkg))
        assertEquals("语音与字幕", MemoryStats.title(pkg, "$pkg:asr"))
        assertEquals("AI 模型", MemoryStats.title(pkg, "$pkg:inference"))
        assertEquals("文字识别", MemoryStats.title(pkg, "$pkg:ocr"))
        assertEquals("other", MemoryStats.title(pkg, "$pkg:other"))
    }
}
