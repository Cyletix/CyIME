package com.kingzcheung.xime.service
import org.junit.Assert.*
import org.junit.Test
class InputTextPolicyTest {
    @Test fun punctuationAndWhitespaceTerminatePredictions() {
        listOf("你好，", "hello!", "“引号”", "hello ", "", "你好\n", "🙂", "1", "你好2", "2026", "０.０５").forEach { assertFalse(it, canPredictAfter(it)) }
        listOf("你好", "输入法", "word").forEach { assertTrue(it, canPredictAfter(it)) }
    }
    @Test fun literalPunctuationNeverMistakesControlKeysOrLetters() {
        listOf(",", "。", "！", "/", "@", "(").forEach { assertTrue(it, isLiteralPunctuation(it)) }
        listOf("delete", "space", "abc", "s", "4", "").forEach { assertFalse(it, isLiteralPunctuation(it)) }
    }

    @org.junit.Test fun widthNormalizesBothAsciiAndPresetChinesePunctuation() {
        assertEquals("、。", punctuationWidth(",.", true, japanese = true))
        org.junit.Assert.assertEquals(",.?!;:()[]+-*/", punctuationWidth("，。？！；：（）［］＋－＊／", false))
        org.junit.Assert.assertEquals("，。？！；：（）［］＋－＊／", punctuationWidth(",.?!;:()[]+-*/", true))
        for (full in listOf(false, true)) for (value in listOf("你好，世界", "1.05", "hi!", "😀", "→", "×")) {
            org.junit.Assert.assertEquals(value, punctuationWidth(value, full))
        }
        org.junit.Assert.assertEquals("\"\"''", punctuationWidth("“”‘’", false))
    }
}
