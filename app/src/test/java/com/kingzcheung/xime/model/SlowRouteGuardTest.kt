package com.kingzcheung.xime.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowRouteGuardTest {
    private val big = 200L * 1024 * 1024
    private val window = SlowRouteGuard.WINDOW_MS

    @Test fun slowBigFileIsSkippedAfterTheWindow() {
        val guard = SlowRouteGuard(enabled = true, totalBytes = big, startMs = 0)
        assertFalse(guard.isSlow(10_000, window - 1))
        assertTrue(guard.isSlow(1024L * 1024, window))
    }

    @Test fun fastRouteKeepsGoingAndIsJudgedOnlyOnce() {
        val guard = SlowRouteGuard(enabled = true, totalBytes = big, startMs = 0)
        assertFalse(guard.isSlow(20L * 1024 * 1024, window))
        // 头 15 秒够快，之后网络抖一下也不中途换线路（已下的部分不白丢）。
        assertFalse(guard.isSlow(20L * 1024 * 1024, window * 4))
    }

    @Test fun lastRouteSmallFilesAndUnknownSizesFollowTheirRules() {
        assertFalse(SlowRouteGuard(enabled = false, totalBytes = big, startMs = 0).isSlow(0, window))
        assertFalse(SlowRouteGuard(enabled = true, totalBytes = 300_000, startMs = 0).isSlow(0, window))
        // 不知道大小（没有 Content-Length）的也按大文件算。
        assertTrue(SlowRouteGuard(enabled = true, totalBytes = -1, startMs = 0).isSlow(0, window))
    }
}
