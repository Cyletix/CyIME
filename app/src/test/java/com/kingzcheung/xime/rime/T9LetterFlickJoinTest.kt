package com.kingzcheung.xime.rime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 九键拂出的字母何时并进数字串（用户 11-03：刚点完数字马上拂动，左栏快照还是空闲，字母被顶掉）。 */
class T9LetterFlickJoinTest {
    @Test fun aQueuedTapCountsAsComposingEvenWhileThePanelStillReadsIdle() {
        // 刚点的数字还在队列里：左栏还没刷新成 INPUT，字母也要并进数字串。
        assertTrue(letterJoinsDigits(leftPanelIdle = true, queuedKeys = 1))
        // 已经在组数字串。
        assertTrue(letterJoinsDigits(leftPanelIdle = false, queuedKeys = 0))
        assertTrue(letterJoinsDigits(leftPanelIdle = false, queuedKeys = 2))
    }

    @Test fun nothingComposingAndNothingQueuedIsAPlainLetter() {
        // 真正空闲：照普通字母进 RIME（整句拂动的第一个字母）。
        assertFalse(letterJoinsDigits(leftPanelIdle = true, queuedKeys = 0))
    }
}
