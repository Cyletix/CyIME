package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NumberHistoryTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun onlyFourToFifteenAsciiDigitsAreLearned() {
        val m = NumberMemory()
        assertFalse(m.record("123", 1))
        assertTrue(m.record("1234", 1))
        assertTrue(m.record("123456789012345", 1))
        // 银行卡、身份证长度不记。
        assertFalse(m.record("6222020200112233445", 1))
        assertFalse(m.record("12a4", 1))
        assertFalse(m.record("１２３４", 1))
    }

    @Test
    fun suggestedAfterTwoUsesFromTwoTypedDigits() {
        val m = NumberMemory()
        m.record("13812345678", 1)
        // 只打过一次：不给。
        assertEquals(emptyList<String>(), m.suggestions("13"))
        m.record("13812345678", 2)
        assertEquals(listOf("13812345678"), m.suggestions("13"))
        assertEquals(listOf("13812345678"), m.suggestions("1381234567"))
        // 打一位不给，打完整串不给（和打的一样）。
        assertEquals(emptyList<String>(), m.suggestions("1"))
        assertEquals(emptyList<String>(), m.suggestions("13812345678"))
        assertEquals(emptyList<String>(), m.suggestions("139"))
    }

    @Test
    fun moreUsesFirstThenMostRecent() {
        val m = NumberMemory()
        repeat(3) { m.record("1300000000", 10L + it) }
        repeat(2) { m.record("1311111111", 20L + it) }
        repeat(2) { m.record("1322222222", 30L + it) }
        assertEquals(listOf("1300000000", "1322222222", "1311111111"), m.suggestions("13"))
    }

    @Test
    fun keepsAtMostMaxEntriesDroppingTheWeakest() {
        val m = NumberMemory()
        m.record("99990000", 0); m.record("99990000", 0)
        for (i in 0 until NumberMemory.MAX_ENTRIES + 10) m.record((10000 + i).toString(), 1L + i)
        assertEquals(2, m.count("99990000"))
        assertFalse(m.contains("10000"))
        assertTrue(m.contains((10000 + NumberMemory.MAX_ENTRIES + 9).toString()))
    }

    @Test
    fun roundTripsAndForgetsOldSingleUses() {
        val now = 100 * day
        val m = NumberMemory()
        m.record("1234567", now - 40 * day)          // 一次，40 天前：忘掉
        m.record("7654321", now - 10 * day)          // 一次，10 天前：留着
        m.record("5555555", now - 90 * day); m.record("5555555", now - 90 * day) // 两次：留着
        val back = NumberMemory.parse(m.serialize() + "garbage\nabc\t1\t1\n", now)
        assertFalse(back.contains("1234567"))
        assertEquals(1, back.count("7654321"))
        assertEquals(2, back.count("5555555"))
    }

    @Test
    fun writeAndReadFile() {
        val dir = Files.createTempDirectory("numhist").toFile()
        try {
            val file = File(dir, NumberHistory.FILE_NAME)
            assertEquals(emptyList<String>(), NumberMemory.read(file).suggestions("12"))
            val m = NumberMemory()
            m.record("12345", System.currentTimeMillis()); m.record("12345", System.currentTimeMillis())
            NumberMemory.write(file, m.serialize())
            NumberMemory.write(file, m.serialize())
            assertEquals(listOf("12345"), NumberMemory.read(file).suggestions("12"))
            assertFalse(File(file.path + ".tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun trackerHandsOverRunWhenNextNumberStarts() {
        val t = NumberRunTracker()
        var text = "电话 "
        for (c in "13812345678") { text += c; assertNull(t.onDigit(text)) }
        text += "，再打 2"
        assertEquals("13812345678", t.onDigit(text))
        // 太短的不交。
        text += " 9"
        assertNull(t.onDigit(text))
    }

    @Test
    fun trackerSkipsUsernamesDecimalsAndEmails() {
        assertNull(NumberRunTracker.learnableRun("user1234"))
        assertNull(NumberRunTracker.learnableRun("3.1415"))
        assertNull(NumberRunTracker.learnableRun("a@1234"))
        assertNull(NumberRunTracker.learnableRun("12:30"))
        assertEquals("1234", NumberRunTracker.learnableRun("编号１２３４"))
        assertEquals("12345678", NumberRunTracker.learnableRun("010-12345678"))
        val t = NumberRunTracker()
        t.onDigit("user1234")
        assertNull(t.onDigit("user1234 5"))
    }

    @Test
    fun resetAndNotLearnableContextDropTheRun() {
        val t = NumberRunTracker()
        t.onDigit("1234")
        t.reset()
        assertNull(t.onDigit("5"))
        // 删了一位再打：从输入框重新读整串，接着跟。
        t.onDigit("12345")
        t.reset()
        t.onDigit("12346")
        assertNull(t.onDigit("123467"))
        assertEquals("123467", t.onDigit("123467 8"))
        // 上一串打完后到了不该记的地方（null）：上一串照样交出，新的不跟。
        t.onDigit("99998")
        assertEquals("99998", t.onDigit(null))
        assertNull(t.onDigit("1"))
    }
}
