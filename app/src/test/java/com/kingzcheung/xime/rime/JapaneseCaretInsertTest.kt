package com.kingzcheung.xime.rime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 日语光标在读音中间时打字（11-05 用户反馈：句中光标处输入的内容出现在句末）。
 * 字母插在光标处；没拼成假名的字母先留在光标处，不和后面的读音连读。
 */
class JapaneseCaretInsertTest {
    @After fun reset() { japaneseLIsLongVowel = true }

    /** 按顺序打 [keys]，光标从编码 [caret] 处开始；返回最后的编码、光标、光标处待拼字母。 */
    private fun type(input: String, caret: Int, keys: String): JapaneseInsertion {
        var state = JapaneseInsertion(input, caret, "")
        for (key in keys) {
            state = insertJapaneseRomaji(state.input.take(state.caret), state.pending + key, state.input.drop(state.caret))
        }
        return state
    }

    @Test fun lettersGoAtTheCaretNotAtTheEnd() {
        // さぶら|いやー 里打 ka：さぶらか|いやー。
        val typed = type("saburaiya-", 6, "ka")
        assertEquals("saburakaiya-", typed.input)
        assertEquals(8, typed.caret)
        assertEquals("", typed.pending)
    }

    @Test fun anUnfinishedConsonantWaitsAtTheCaret() {
        // 只打了 k：编码不变（不会和后面的 い 连成 き），k 留在光标处显示。
        val half = type("saburaiya-", 6, "k")
        assertEquals("saburaiya-", half.input)
        assertEquals(6, half.caret)
        assertEquals("k", half.pending)
        // 拗音、sh 这类也等元音。
        assertEquals("ky", type("saburaiya-", 6, "ky").pending)
        assertEquals("sabura" + "kya" + "iya-", type("saburaiya-", 6, "kya").input)
    }

    @Test fun nAndDoubledConsonantsAreWrittenSoTheyCannotMerge() {
        // ん 后面是元音：写成 nn，不变成 な。
        assertEquals("sabura" + "nn" + "iya-", type("saburaiya-", 6, "nn").input)
        val nk = type("saburaiya-", 6, "nk")
        assertEquals("sabura" + "nn" + "iya-", nk.input)
        assertEquals("k", nk.pending)
        // っ：kk 的第一个 k 写成 xtsu。
        val kk = type("saburaiya-", 6, "kk")
        assertEquals("sabura" + "xtsu" + "iya-", kk.input)
        assertEquals("k", kk.pending)
    }

    @Test fun theTextBeforeTheCaretKeepsItsReading() {
        // かん|か：光标前的 ん 是单个 n，插元音前写成 nn，不会读成 かな。
        assertEquals("kann" + "a" + "ka", type("kanka", 3, "a").input)
        // かっ|か：促音的 k 写成 xtsu，不会读成 かき。
        assertEquals("kaxtsu" + "i" + "ka", type("kakka", 3, "i").input)
    }

    @Test fun insertingAtTheStartAndTheLongVowel() {
        assertEquals("a" + "iya-", type("iya-", 0, "a").input)
        assertEquals(1, type("iya-", 0, "a").caret)
        // 长音 - 、当长音的 L 本身就是一个假名。
        assertEquals("ko" + "-" + "hi-", type("kohi-", 2, "-").input)
        assertEquals("ko" + "l" + "hi-", type("kohi-", 2, "l").input)
        // L 是字母时（长音键单独放）la 才是一个假名。
        japaneseLIsLongVowel = false
        assertEquals("l", type("kohi-", 2, "l").pending)
        assertEquals("ko" + "la" + "hi-", type("kohi-", 2, "la").input)
    }

    @Test fun deletingStillKeepsCompletedKana() {
        // 抽出来的 stableJapaneseRomaji 不改原来的退格：kan + k 删掉待拼的 k，ん 写成 nn 留着。
        assertEquals("kann", deleteJapaneseRomaji("kank"))
        assertEquals("kaxtsu", deleteJapaneseRomaji("kakk"))
        assertEquals("ka", stableJapaneseRomaji("ka"))
        assertEquals("kann", stableJapaneseRomaji("kan"))
        assertEquals("KAXTSU", stableJapaneseRomaji("KAK"))
    }

    @Test fun boundariesAfterInsertingFollowTheKana() {
        // さ ぶ ら | い や ー：插 ka 后 か 是新的一格，后面的边界整体后挪。
        val before = listOf(0, 2, 4, 6, 7, 9, 10)
        assertEquals(before, japaneseKanaStarts("saburaiya-"))
        val ka = type("saburaiya-", 6, "ka")
        assertEquals(listOf(0, 2, 4, 6, 8, 9, 11, 12), boundariesAfterInsertion("saburaiya-", before, 6, ka))
        assertEquals(japaneseKanaStarts(ka.input), boundariesAfterInsertion("saburaiya-", before, 6, ka))
        // 只打了 k：编码没变，边界不变。
        assertEquals(before, boundariesAfterInsertion("saburaiya-", before, 6, type("saburaiya-", 6, "k")))
        // かん|か + a：ん 写成 nn 那一格变长。
        val kanka = japaneseKanaStarts("kanka")
        assertEquals(listOf(0, 2, 3, 5), kanka)
        val a = type("kanka", 3, "a")
        assertEquals(listOf(0, 2, 4, 5, 7), boundariesAfterInsertion("kanka", kanka, 3, a))
        assertEquals(5, a.caret)
    }
}
