package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 雾凇 26 键的 Unicode 码（大写 U 加十六进制）：角标数字接进编码、别的字母把码原样上屏，判断都看这两个函数。 */
class UnicodeCodeInputTest {
    @Test fun capitalUWithHexIsACode() {
        for (input in listOf("U", "U2", "U2603", "U62fc", "U1f600", "Uab")) assertTrue(input, isUnicodeCodeInput(input))
    }

    @Test fun anythingElseIsNot() {
        // 小写 u、大写十六进制、拼音和英文都不算：UEFA、Uber 照旧是英文。
        for (input in listOf("", "u2603", "U26FF", "UEFA", "Uber", "U+2603", "nihao", "2603", "U2k"))
            assertFalse(input, isUnicodeCodeInput(input))
    }

    @Test fun onlyACodeWithADigitCommitsBeforeAnotherLetter() {
        assertTrue(isUnicodeCodeWithDigit("U2"))
        assertTrue(isUnicodeCodeWithDigit("U26a"))
        // 只有字母（Ub、Ubad）是在打英文，后面的字母照常接上（Uber）。
        assertFalse(isUnicodeCodeWithDigit("U"))
        assertFalse(isUnicodeCodeWithDigit("Ubad"))
    }
}
