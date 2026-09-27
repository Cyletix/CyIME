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

    @Test fun japaneseKeyboardSpacesAndDigitsFollowWidth() {
        assertEquals("　", keyboardLiteralWidth(" ", true, true))
        assertEquals(" ", keyboardLiteralWidth("　", false, true))
        assertEquals("０１２３４５６７８９", keyboardLiteralWidth("0123456789", true, true))
        assertEquals("0123456789", keyboardLiteralWidth("０１２３４５６７８９", false, true))
        assertEquals("１２．５＋３", keyboardLiteralWidth("12.5+3", true, true, numberPanel = true))
        assertEquals("12.5+3", keyboardLiteralWidth("１２．５＋３", false, true, numberPanel = true))
        assertEquals("、。", keyboardLiteralWidth(",.", true, true))
        assertEquals("よy", keyboardLiteralWidth("よy", true, true))
    }
    @Test fun otherLanguagesKeepTheirSpaceAndNumericBehavior() {
        assertEquals(" ", keyboardLiteralWidth(" ", true, false))
        assertEquals("123", keyboardLiteralWidth("123", true, false))
        assertEquals("1.5", keyboardLiteralWidth("1.5", true, false, numberPanel = true))
        assertEquals("，", keyboardLiteralWidth(",", true, false))
        assertEquals(",", keyboardLiteralWidth(",", false, false))
    }
    @Test fun explicitlyChosenSymbolsKeepTheirExactCodepointsAcrossWidthAndLanguage() {
        val literals = listOf("-", "－", ",", "，", ".", "。", "123", "１２３", " ", "　", "a-b")
        for (full in listOf(false, true)) for (japanese in listOf(false, true))
            for (numberPanel in listOf(false, true)) for (literal in literals) {
                assertEquals(literal, keyboardLiteralWidth(literal, full, japanese, numberPanel, preserveWidth = true))
            }
        assertEquals("－", keyboardLiteralWidth("-", true, false))
        assertEquals("-", keyboardLiteralWidth("－", false, false))
    }

}
