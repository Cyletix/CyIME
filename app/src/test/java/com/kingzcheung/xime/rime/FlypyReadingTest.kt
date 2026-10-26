package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Test

/** 14 键小鹤双拼：屏上编码、回车和原样上屏都是首选读音的小鹤双拼字母，不是合并编码（10-27）。 */
class FlypyReadingTest {
    @Test fun syllablesFollowTheFlypySpeller() {
        mapOf("ni" to "ni", "hao" to "hc", "wo" to "wo", "men" to "mf", "shi" to "ui", "zhuang" to "vl", "chuan" to "ir",
            "xiang" to "xl", "jiong" to "js", "ying" to "yk", "kuai" to "kk", "yue" to "yt", "nüe" to "nt", "lv" to "lv",
            "a" to "aa", "e" to "ee", "ang" to "ah", "eng" to "eg", "ai" to "ai", "en" to "en", "er" to "er", "ou" to "ou",
            "zhui" to "vv", "qiu" to "qq", "dian" to "dm", "xiao" to "xn", "gua" to "gx", "lin" to "lb", "dei" to "dw")
            .forEach { (pinyin, keys) -> assertEquals(pinyin, keys, flypySyllable(pinyin)) }
        assertEquals("ni hc", flypyReading("ni hao"))
    }

    @Test fun mergedCodesShowTheFirstCandidatesFlypyLetters() {
        // bn ui gh cv 打 你好：编码 BUGC、引擎回显 bu'gc，首选读音 ni hao。
        assertEquals("ni'hc", merged14Preedit("BUGC", "bu'gc", flypyReading("ni hao")))
        assertEquals("wo'mf", merged14Preedit("QOMD", "qo'md", flypyReading("wo men")))
        // 读音和按键对不上（首选只用了一部分编码）时保留引擎回显。
        assertEquals("bu'gc", merged14Preedit("BUGC", "bu'gc", flypyReading("ni")))
        // 全拼 14 键照旧。
        assertEquals("ni'hao", merged14Preedit("BUGaO", "bu'gao", "ni hao"))
    }
}
