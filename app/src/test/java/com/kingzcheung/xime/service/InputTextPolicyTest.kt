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
}
