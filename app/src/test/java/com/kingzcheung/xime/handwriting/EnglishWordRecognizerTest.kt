package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 整词英文手写解码时能出的记号：字母和 . , ? !，不出数字、公式命令和其它符号。 */
class EnglishWordRecognizerTest {
    @Test fun lettersAndSentencePunctuationAreAllowed() {
        for (t in listOf("a", "Z", ".", ",", "?", "!")) assertTrue(t, EnglishWordRecognizer.allowed(t))
    }

    @Test fun digitsCommandsAndOtherSymbolsAreNot() {
        for (t in listOf("1", "\\cdot", "_", "{", "+", "=", "ab", "")) assertFalse(t, EnglishWordRecognizer.allowed(t))
    }
}
