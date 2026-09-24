package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class VoiceRecognitionHandlerTest {
    @Test fun `normalization preserves english words and separates punctuation`() {
        assertEquals("Hello world How are you", normalizeVoiceText("Hello, world! How are you?"))
        assertEquals("你好 世界 こんにちは", normalizeVoiceText("你好，世界。こんにちは！"))
        assertEquals("test one two", normalizeVoiceText("  test\n one\t—two…  "))
        assertEquals("你好 世界", normalizeVoiceText("你好 世界"))
        assertEquals("", normalizeVoiceText(" ，。？！  "))
    }

    @Test fun `normalization is stable across partial final and timeout processing`() {
        listOf("你好吗", "Hello, world!", "こんにちは。", "第一句；第二句。", " ").forEach {
            val clean = normalizeVoiceText(it)
            assertEquals(clean, normalizeVoiceText(clean))
            assertFalse(clean.endsWith(" "))
            assertFalse(clean.endsWith("。"))
        }
    }

    @Test fun `numeric notation survives model cleanup`() {
        assertEquals("0.05 0.1 -0.05 1+2=3 5-2=3 3×4÷2=6 6/2 2*3 50% 1,234.56",
            normalizeVoiceText("0.05，0.1。-0.05；1+2=3！5-2=3。3×4÷2=6，6/2，2*3，50%，1,234.56。"))
        assertEquals("(0.05 + 2) / 3", normalizeVoiceText("(0.05 + 2) / 3。"))
        assertEquals("2 / -3 -(.05 + 1) 2*-0.05", normalizeVoiceText("2 / -3，-(.05 + 1)，2*-0.05。"))
    }

    @Test fun `only four spoken punctuation names become symbols after automatic punctuation cleanup`() {
        assertEquals("你好，世界。可以吗？真好！", normalizeVoiceText("你好，逗号 世界 句号 可以吗 问号 真好 叹号。"))
        assertEquals("冒号 分号 引号 括号 加减乘除", normalizeVoiceText("冒号 分号 引号 括号 加减乘除。"))
        assertEquals("0.05。", normalizeVoiceText("0.05句号。"))
        assertEquals("！", normalizeVoiceText("感叹号"))
    }
}
