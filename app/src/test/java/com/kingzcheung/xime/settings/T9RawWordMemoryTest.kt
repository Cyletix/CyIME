package com.kingzcheung.xime.settings

import com.kingzcheung.xime.service.CandidateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class T9RawWordMemoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val rimeDir: File get() = temp.root

    // 测试统一用「开启档」的阈值：默认值已改为 0（关闭），不能让用例被默认值带成关闭态。
    private val ENABLED_THRESHOLD = T9RawWordMemory.THRESHOLD_STEPS.first { it > 0 }

    private fun policy(
        threshold: Int = ENABLED_THRESHOLD,
        position: T9RawWordMemory.Position = T9RawWordMemory.Position.TOP,
        ratioPercent: Int = T9RawWordMemory.DEFAULT_RATIO_PERCENT,
    ) = T9RawWordMemory.Policy(threshold, position, ratioPercent)

    @Test
    fun defaultPolicyIsOff() {
        // 新装用户默认关闭：不写、不召回，显式开启后才生效。
        assertFalse(T9RawWordMemory.Policy().enabled)
        assertEquals(0, T9RawWordMemory.DEFAULT_THRESHOLD)
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        assertTrue(T9RawWordMemory.lookup(rimeDir, digits, T9RawWordMemory.Policy()).isEmpty())
    }

    private fun baseCandidates(n: Int): List<String> = (0 until n).map { "候选$it" }

    private fun inject(
        texts: List<String>,
        digits: String,
        entries: List<T9RawWordMemory.Entry>,
        policy: T9RawWordMemory.Policy = policy(),
    ): T9RawWordMemory.Injected = T9RawWordMemory.inject(
        texts, List(texts.size) { "" },
        texts.mapIndexed { i, _ -> CandidateAction.engine(i) }, digits, entries, policy)

    @Test
    fun digitsUseTheNineKeyTable() {
        assertEquals("946628483", T9RawWordMemory.digitsFor("winnative"))
        assertEquals("946628483", T9RawWordMemory.digitsFor("Winnative"))
        assertEquals("22", T9RawWordMemory.digitsFor("ab"))
    }

    @Test
    fun onlyLiteralWordsAreLearnable() {
        assertTrue(T9RawWordMemory.isForcedWord("winnative"))
        assertTrue(T9RawWordMemory.isForcedWord("CS2"))
        assertFalse(T9RawWordMemory.isForcedWord("你好"))
        assertFalse(T9RawWordMemory.isForcedWord("a"))
    }

    @Test
    fun firstTwoCommitsOnlyCount() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        assertEquals(1, T9RawWordMemory.remember(rimeDir, digits, "winnative"))
        assertEquals(2, T9RawWordMemory.remember(rimeDir, digits, "winnative"))
        // 默认档 3 次：第 1、2 次只记，不进候选。
        assertTrue(T9RawWordMemory.lookup(rimeDir, digits, policy()).isEmpty())
    }

    @Test
    fun thirdCommitShowsAtTop() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(ENABLED_THRESHOLD) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        assertEquals(1, entries.size)
        assertEquals(3, entries.first().count)

        val injected = inject(baseCandidates(8), digits, entries, policy())
        assertEquals("winnative", injected.texts[0])
        assertTrue(injected.actions[0].isRememberedCandidate)
    }

    @Test
    fun thresholdFiveWaitsUntilFifthCommit() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(4) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        assertTrue(T9RawWordMemory.lookup(rimeDir, digits, policy(threshold = 5)).isEmpty())

        T9RawWordMemory.remember(rimeDir, digits, "winnative")
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy(threshold = 5))
        assertEquals(1, entries.size)
        assertEquals(5, entries.first().count)
    }

    @Test
    fun thresholdZeroDisablesRecallWithoutTouchingData() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(5) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }

        val off = policy(threshold = 0)
        assertFalse(off.enabled)
        assertTrue(T9RawWordMemory.lookup(rimeDir, digits, off).isEmpty())
        // 关闭时注入原样返回，不占位。
        val base = baseCandidates(8)
        assertEquals(base, inject(base, digits, T9RawWordMemory.lookup(rimeDir, digits, policy()), off).texts)
        // 已记数据保留：重新打开后仍是 5 次。
        val back = T9RawWordMemory.lookup(rimeDir, digits, policy(threshold = 5))
        assertEquals(5, back.first().count)
    }

    @Test
    fun secondPositionInsertsAtSecond() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        val injected = inject(baseCandidates(8), digits, entries,
            policy(position = T9RawWordMemory.Position.SECOND))
        assertEquals("候选0", injected.texts[0])
        assertEquals("winnative", injected.texts[1])
    }

    @Test
    fun sixthPositionInsertsAtSixth() {
        val digits = T9RawWordMemory.digitsFor("winnative")
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        val injected = inject(baseCandidates(8), digits, entries,
            policy(position = T9RawWordMemory.Position.SIXTH))
        assertEquals("候选4", injected.texts[4])
        assertEquals("winnative", injected.texts[5])
        assertEquals("候选5", injected.texts[6])
    }

    @Test
    fun sixthPositionFallsBackToEndInOrder() {
        val digits = "946628483"
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winner") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        // 候选只有 3 个，放不到第 6 位：按命中顺序追加到末尾，不能倒序。
        // 同次数时最近写的在前，所以 winner 先于 winnative。
        val injected = inject(baseCandidates(3), digits, entries,
            policy(position = T9RawWordMemory.Position.SIXTH))
        assertEquals(5, injected.texts.size)
        assertEquals("winner", injected.texts[3])
        assertEquals("winnative", injected.texts[4])
    }

    @Test
    fun duplicateCandidatesAreNotInsertedAgain() {
        val digits = "946628483"
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        val base = listOf("winnative", "候选1", "候选2", "候选3")
        assertEquals(base, inject(base, digits, entries, policy()).texts)
    }

    @Test
    fun multipleHitsKeepOnlyUpToThree() {
        val digits = "946628483"
        repeat(4) { T9RawWordMemory.remember(rimeDir, digits, "winnative") }
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winner") }
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winter") }
        repeat(3) { T9RawWordMemory.remember(rimeDir, digits, "winkel") }
        val entries = T9RawWordMemory.lookup(rimeDir, digits, policy())
        assertEquals(4, entries.size)
        val injected = inject(baseCandidates(8), digits, entries, policy())
        // 同一轮最多插 3 个：次数高的在前，同次数时最近写的在前。
        assertEquals("winnative", injected.texts[0])
        assertEquals("winkel", injected.texts[1])
        assertEquals("winter", injected.texts[2])
        assertEquals("候选0", injected.texts[3])
    }

    @Test
    fun topWordRecalledByShortPrefix_winnative() {
        val full = T9RawWordMemory.digitsFor("winnative") // 946628483，9 位
        repeat(3) { T9RawWordMemory.remember(rimeDir, full, "winnative") }
        // 默认 60%：ceil(9*0.6)=6，打 6 位可召回、5 位不行。
        assertTrue(T9RawWordMemory.lookup(rimeDir, "94662", policy()).isEmpty())
        val entries = T9RawWordMemory.lookup(rimeDir, "946628", policy())
        assertEquals(1, entries.size)
        assertTrue(entries.first().prefixHit)
        assertEquals("winnative", inject(baseCandidates(8), "946628", entries, policy()).texts[0])
        // 上屏动作带完整码，点选时按全词计数。
        val injected = inject(listOf("候选0", "候选1"), "946628", entries, policy())
        assertEquals(full, injected.actions[0].rememberedDigits)
    }

    @Test
    fun topWordRecalledByShortPrefix_gamenative() {
        val full = T9RawWordMemory.digitsFor("gamenative") // 4263628483，10 位
        repeat(3) { T9RawWordMemory.remember(rimeDir, full, "gamenative") }
        // 恰好六成：ceil(10*0.6)=6，打 6 位就能召回。
        assertEquals(1, T9RawWordMemory.lookup(rimeDir, "426362", policy()).size)
        assertTrue(T9RawWordMemory.lookup(rimeDir, "42636", policy()).isEmpty())
    }

    @Test
    fun ratioThirtyPercentStillNeedsFourDigits() {
        val full = T9RawWordMemory.digitsFor("abcde") // 22233，5 位
        repeat(3) { T9RawWordMemory.remember(rimeDir, full, "abcde") }
        val policy = policy(ratioPercent = 30)
        // 30% 只要 2 位，但保底 4 位：打 3 位不召回，4 位才召回。
        assertTrue(T9RawWordMemory.lookup(rimeDir, "222", policy).isEmpty())
        assertEquals(1, T9RawWordMemory.lookup(rimeDir, "2223", policy).size)
    }

    @Test
    fun ratioNinetyPercentDelaysPrefixRecall() {
        val full = T9RawWordMemory.digitsFor("winnative") // 9 位
        repeat(3) { T9RawWordMemory.remember(rimeDir, full, "winnative") }
        val policy = policy(ratioPercent = 90)
        // ceil(9*0.9)=9，短码一律不召回。
        assertTrue(T9RawWordMemory.lookup(rimeDir, "94662848", policy).isEmpty())
        // 打全码走精确命中。
        assertEquals(1, T9RawWordMemory.lookup(rimeDir, full, policy).size)
    }

    @Test
    fun prefixRecallRequiresThreshold() {
        val full = T9RawWordMemory.digitsFor("winnative")
        repeat(2) { T9RawWordMemory.remember(rimeDir, full, "winnative") }
        // 次数不够，短码也不召回。
        assertTrue(T9RawWordMemory.lookup(rimeDir, "946628", policy()).isEmpty())
    }
}
