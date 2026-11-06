package com.kingzcheung.xime.service

import com.kingzcheung.xime.service.CtrlEnterSend.Outcome
import com.kingzcheung.xime.service.CtrlEnterSend.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/** 用户 11-05：Claude 里 Ctrl+回车发不出去。发送动作没反应才补回车键，补出来的只是换行就删掉。 */
class CtrlEnterSendTest {
    private val typed = Snapshot("你好", "")

    @Test fun clearedBoxCountsAsSent() {
        assertEquals(Outcome.CHANGED, CtrlEnterSend.outcome(typed, Snapshot("", "")))
    }

    @Test fun untouchedBoxMeansTheAppIgnoredIt() {
        assertEquals(Outcome.UNCHANGED, CtrlEnterSend.outcome(typed, Snapshot("你好", "")))
    }

    @Test fun onlyANewlineAtTheCursorIsTakenBack() {
        assertEquals(Outcome.NEWLINE_ADDED, CtrlEnterSend.outcome(typed, Snapshot("你好\n", "")))
        assertEquals(Outcome.NEWLINE_ADDED, CtrlEnterSend.outcome(Snapshot("前", "后"), Snapshot("前\n", "后")))
        // 用户接着打了字、或换行不在光标处：不删。
        assertEquals(Outcome.CHANGED, CtrlEnterSend.outcome(typed, Snapshot("你好\n啊", "")))
        assertEquals(Outcome.CHANGED, CtrlEnterSend.outcome(Snapshot("前", "后"), Snapshot("前", "\n后")))
    }

    @Test fun fullReadWindowStillSeesTheNewline() {
        val long = "字".repeat(4096)
        assertEquals(Outcome.NEWLINE_ADDED, CtrlEnterSend.outcome(Snapshot(long, ""), Snapshot(long.drop(1) + "\n", "")))
    }

    @Test fun lostConnectionCountsAsSent() {
        assertEquals(Outcome.CHANGED, CtrlEnterSend.outcome(typed, null))
    }
}
