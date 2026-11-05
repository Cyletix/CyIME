package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmissionPauseTest {
    private fun typed(pause: OmissionPause, gapMs: Long, letters: Int, start: Long = 1_000L): Long {
        var now = start
        for (length in 1..letters) { pause.onComposition(length, now); now += gapMs }
        return now
    }

    /** 打得快的人（每键 100 ms）和没有节奏数据时仍是原来的 180 ms。 */
    @Test fun fastTypingKeepsTheOriginalPause() {
        assertEquals(180L, OmissionPause().delayMs())
        val pause = OmissionPause()
        typed(pause, 100L, 10)
        assertEquals(180L, pause.delayMs())
    }

    /** 每键 260 ms 打字时 180 ms 的停顿几乎每个键都触发补算；按节奏等到 390 ms，打字途中不再补算。 */
    @Test fun slowerTypingWaitsLongerThanItsOwnKeyGap() {
        val pause = OmissionPause()
        typed(pause, 260L, 12)
        assertEquals(390L, pause.delayMs())
        val slow = OmissionPause()
        typed(slow, 900L, 12)
        assertEquals(450L, slow.delayMs())
    }

    /** 两段拼音之间的停顿、删字、整段换掉都不算字母间隔。 */
    @Test fun onlySingleLetterStepsCountAsTypingRhythm() {
        val pause = OmissionPause()
        var now = typed(pause, 300L, 6)
        assertEquals(450L, pause.delayMs())
        pause.onComposition(0, now); now += 50L         // 上屏
        pause.onComposition(1, now); now += 2_000L      // 新拼音第一个字母不算
        pause.onComposition(2, now); now += 50L         // 2 秒的间隔是停顿，不算
        pause.onComposition(1, now); now += 50L         // 退格
        pause.onComposition(5, now); now += 50L         // 粘贴/改拼音
        pause.onComposition(4, now)
        assertEquals(450L, pause.delayMs())
    }

    /** 某个长度补算超时，之后这么长和更长的拼音不再补算；短的照常；过了重试时间再试。 */
    @Test fun slowRefineSkipsThisAndLongerInputsForAWhile() {
        val pause = OmissionPause(budgetMs = 100L, retryMs = 60_000L)
        assertTrue(pause.withinBudget(40, 0L))
        pause.onRefined(40, 400L, 10_000L)
        assertFalse(pause.withinBudget(40, 10_001L))
        assertFalse(pause.withinBudget(60, 10_001L))
        assertTrue(pause.withinBudget(20, 10_001L))
        assertTrue(pause.withinBudget(40, 70_001L))
    }

    /** 快的机器测不到超时，什么都不跳过。 */
    @Test fun fastRefinesNeverSkip() {
        val pause = OmissionPause()
        for (length in 4..80) pause.onRefined(length, 30L, length.toLong())
        for (length in 4..80) assertTrue(pause.withinBudget(length, 100L))
    }

    /** 过了重试时间再测得不慢，就恢复补算。 */
    @Test fun aLaterFastRefineClearsTheBucket() {
        val pause = OmissionPause()
        pause.onRefined(33, 300L, 5L)
        assertFalse(pause.withinBudget(33, 6L))
        pause.onRefined(35, 20L, 7L)
        assertTrue(pause.withinBudget(33, 8L))
    }
}
