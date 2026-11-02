package com.kingzcheung.xime.stats

import com.kingzcheung.xime.stats.TypingStatsData.Day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingStatsDataTest {
    @Test
    fun countsCharsWithoutWhitespaceAndKeepsSupplementaryAsOne() {
        assertEquals(4, TypingStatsText.countChars("你好 ab"))
        assertEquals(2, TypingStatsText.countChars("😀𠀀"))
        assertEquals(0, TypingStatsText.countChars(" \n"))
    }

    @Test
    fun totalsSurvivePruningOfOldDays() {
        val data = TypingStatsData()
        data.add(10, Day.CHARS, 100)
        data.add(500, Day.CHARS, 5)
        data.prune(500)
        assertEquals(0, data.chars(10))
        assertEquals(105, data.total[Day.CHARS])
        assertEquals(100, data.bestDayChars)
        assertEquals(10, data.bestDay)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val data = TypingStatsData()
        data.add(20000, Day.CHARS, 1234)
        data.add(20001, Day.KEYS, 77)
        data.hours[21] = 900
        data.sources[TypingStatsData.Source.VOICE.ordinal] = 33
        data.recordPick(20001, 2, "nu=ni hao")
        data.bestSpeed = 88
        val back = TypingStatsData.decode(data.encode())
        assertEquals(data.encode(), back.encode())
        assertEquals(1234, back.chars(20000))
        assertEquals(88, back.bestSpeed)
    }

    @Test
    fun brokenLinesAreSkipped() {
        val back = TypingStatsData.decode("v=1\ntotal=5,x,7\ngarbage\nd12=3\npairs=9999:4,1:2\nbest=oops")
        assertEquals(5, back.total[Day.CHARS])
        assertEquals(0, back.total[Day.PASTED])
        assertEquals(3, back.chars(12))
        assertEquals(2, back.wrongPairs[1])
        assertEquals(0, back.bestSpeed)
    }

    @Test
    fun mergeAddsCountsAndKeepsBestRecords() {
        val stored = TypingStatsData().apply { add(1, Day.CHARS, 50); bestSpeed = 60 }
        val fresh = TypingStatsData().apply { add(1, Day.CHARS, 20); add(2, Day.CHARS, 10); bestSpeed = 40 }
        val merged = stored.plus(fresh)
        assertEquals(70, merged.chars(1))
        assertEquals(80, merged.total[Day.CHARS])
        assertEquals(60, merged.bestSpeed)
        assertEquals(70, merged.bestDayChars)
        assertEquals(1, merged.bestDay)
    }

    @Test
    fun pickCountsFirstCandidateAndCorrectionKinds() {
        val data = TypingStatsData()
        data.recordPick(1, 0, "")
        data.recordPick(1, 1, "nu=ni hao")      // typed u for i: a replace
        data.recordPick(1, 0, "zhogn=zhong")    // swapped g and n
        data.recordPick(1, 0, "zhng=zhong")     // missing o
        data.recordPick(1, 0, "niii=ni")        // two extra i
        data.recordPick(1, 0, "ni hao")         // marks without a fix are not a correction
        assertEquals(6, data.total[Day.PICKS])
        assertEquals(5, data.total[Day.FIRST_PICKS])
        assertEquals(4, data.total[Day.CORRECTIONS])
        assertEquals(1, data.fixes[TypingStatsData.Fix.REPLACE.ordinal])
        assertEquals(1, data.fixes[TypingStatsData.Fix.SWAP.ordinal])
        assertEquals(1, data.fixes[TypingStatsData.Fix.MISSING.ordinal])
        assertEquals(2, data.fixes[TypingStatsData.Fix.EXTRA.ordinal])
        // wanted i, pressed u
        assertEquals(1, data.wrongPairs[('u' - 'a') * 26 + ('i' - 'a')])
        assertEquals(listOf(Triple('i', 'i', 2L), Triple('u', 'i', 1L)), data.topWrongPairs(2))
        assertEquals(1, data.wrongKeyCounts()['u' - 'a'])
    }

    @Test
    fun streakAllowsTodayToBeEmptyYet() {
        val data = TypingStatsData()
        data.add(8, Day.CHARS, 1); data.add(9, Day.CHARS, 1)
        assertEquals(2, data.streak(10))
        data.add(10, Day.CHARS, 1)
        assertEquals(3, data.streak(10))
        assertEquals(0, data.streak(12))
    }

    @Test
    fun ratiosNeedEnoughData() {
        val data = TypingStatsData()
        assertNull(data.keysPerChar())
        assertNull(data.firstPickRate())
        assertNull(data.speed(1))
        data.add(1, Day.KEYS, 260); data.add(1, Day.DELETES, 20); data.add(1, Day.KEY_CHARS, 100)
        assertEquals(2.4, data.keysPerChar()!!, 1e-9)
        data.add(1, Day.ACTIVE_MS, 60_000); data.add(1, Day.ACTIVE_CHARS, 75)
        assertEquals(75L, data.speed(1))
    }

    @Test
    fun textFormatsAndTitles() {
        assertEquals("1,234", TypingStatsText.count(1234))
        assertEquals("1.2 万", TypingStatsText.count(12_345))
        assertEquals("41 万", TypingStatsText.count(410_000))
        assertEquals("初来乍到", TypingStatsText.titleFor(0).name)
        assertEquals("笔耕不辍", TypingStatsText.titleFor(10_000).name)
        assertEquals("百万字作者", TypingStatsText.titleFor(5_000_000).name)
        assertNull(TypingStatsText.nextTitle(2_000_000))
        val (next, left) = TypingStatsText.nextTitle(999)!!
        assertEquals("渐入佳境", next.name)
        assertEquals(1, left)
        assertTrue(TypingStatsText.titles.zipWithNext().all { (a, b) -> a.min < b.min })
    }
}
