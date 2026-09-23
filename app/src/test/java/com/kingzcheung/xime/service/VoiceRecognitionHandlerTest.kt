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
}
