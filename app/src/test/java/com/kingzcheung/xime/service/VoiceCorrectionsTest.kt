package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「语音纠错」：用户列的错词上屏前换成正词，一次替换，不连锁。 */
class VoiceCorrectionsTest {
    private fun fix(rules: String, text: String) = VoiceCorrections.parse(rules).apply(text)

    @Test fun replacesListedWords() {
        val rules = "南崩 难绷\n塞伯朋克 赛博朋克"
        assertEquals("这也太难绷了，赛博朋克。", fix(rules, "这也太南崩了，塞伯朋克。"))
        assertEquals("难绷难绷", fix(rules, "南崩南崩"))
        assertEquals("没有错词", fix(rules, "没有错词"))
    }

    @Test fun separators() {
        assertEquals("南崩" to "难绷", VoiceCorrections.parseLine("  南崩   难绷 "))
        assertEquals("南崩" to "难绷", VoiceCorrections.parseLine("南崩=难绷"))
        assertEquals("南崩" to "难绷", VoiceCorrections.parseLine("南崩 → 难绷"))
        assertEquals("南崩" to "难绷", VoiceCorrections.parseLine("南崩->难绷"))
        assertEquals("南崩" to "难绷", VoiceCorrections.parseLine("南崩\t难绷"))
        assertEquals("cyber punk" to "Cyberpunk", VoiceCorrections.parseLine("cyber punk = Cyberpunk"))
        assertNull(VoiceCorrections.parseLine(""))
        assertNull(VoiceCorrections.parseLine("南崩"))
        assertNull(VoiceCorrections.parseLine("难绷 难绷"))
        assertEquals(2, VoiceCorrections.parse("南崩 难绷\n\n只有一个词\n塞伯朋克 赛博朋克\n").size)
    }

    @Test fun longestFirstAndNoChain() {
        assertEquals("赛博朋克", fix("塞伯 赛博\n塞伯朋克 赛博朋克", "塞伯朋克"))
        // 甲→乙、乙→丙：换上去的乙不再被换成丙。
        assertEquals("乙", fix("甲 乙\n乙 丙", "甲"))
        assertEquals("乙丙", fix("甲 乙\n乙 丙", "甲乙"))
        // 正词里含错词也不会越换越长。
        assertEquals("他们", fix("他 他们", "他"))
    }

    @Test fun laterLineWins() = assertEquals("难绷", fix("南崩 南蹦\n南崩 难绷", "南崩"))

    @Test fun englishWholeWordsIgnoringCase() {
        val rules = "cat = Kat\nwifi = Wi-Fi"
        assertEquals("Kat category Kat", fix(rules, "cat category Cat"))
        assertEquals("连上Wi-Fi了", fix(rules, "连上WiFi了"))
        assertEquals("wifis", fix(rules, "wifis"))
    }

    // 中文没有词边界：错词出现在更长的词里也会换，词表要写得够长。
    @Test fun chineseMatchesInsideLongerText() = assertEquals("云难绷塌", fix("南崩 难绷", "云南崩塌"))

    @Test fun emptyListLeavesTextAlone() {
        assertEquals("南崩", fix("", "南崩"))
        assertEquals(0, VoiceCorrections.of("").size)
    }

    @Test fun cacheFollowsTheText() {
        assertEquals("难绷", VoiceCorrections.of("南崩 难绷").apply("南崩"))
        assertEquals("南蹦", VoiceCorrections.of("南崩 南蹦").apply("南崩"))
    }

    // 预览只说到一半时不动，说完整了再换，和定稿一致。
    @Test fun partialThenFinal() {
        val c = VoiceCorrections.parse("南崩 难绷")
        assertEquals("太南", c.apply("太南"))
        assertEquals("太难绷", c.apply("太南崩"))
    }
}
