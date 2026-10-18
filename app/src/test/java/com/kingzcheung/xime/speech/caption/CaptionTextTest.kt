package com.kingzcheung.xime.speech.caption

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionTextTest {
    @Test fun `punctuation survives, tags and token spacing do not`() {
        assertEquals("明天上午我来接你，去医院复查。", cleanCaptionText("<|zh|><|NEUTRAL|><|Speech|><|withitn|>明 天 上 午 我 来 接 你 ，去 医 院 复 查 。"))
        assertEquals("记得带上次的化验单", cleanCaptionText("记 得 带 上 次 的 化 验 单"))
        assertEquals("hello world 你好", cleanCaptionText("hello world 你 好"))
    }

    @Test fun `no speech is empty`() {
        assertEquals("", cleanCaptionText("<|nospeech|>", "<|nospeech|>"))
        assertEquals("", cleanCaptionText("anything", "nospeech"))
    }

    @Test fun `event label drops the wrapper and the plain speech value`() {
        assertEquals("BGM", captionEvent("<|BGM|>"))
        assertEquals("", captionEvent("<|Speech|>"))
        assertEquals("", captionEvent(""))
    }
}
