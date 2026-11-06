package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 宿主重启同一个输入框后，听写回到自己写到的地方接着写（11-06 B 站评论框：说着说着停了、光标跑到最前面）。 */
class VoiceHostRestartTest {
    @Test fun `caret the host dropped to the start goes back to the end of the dictation`() {
        val text = "能创建啊 我能创建 试了一下没问题"
        assertEquals(text.length, voiceResumeCaret(text, 0, listOf(text.length), "试了一下没问题"))
    }

    @Test fun `dictation in the middle of the text resumes there`() {
        val text = "开头 你是什么设备啊 安卓结尾"
        val at = text.indexOf("结尾")
        assertEquals(at, voiceResumeCaret(text, 0, listOf(at), "设备啊 安卓"))
    }

    @Test fun `later candidate is used when the first no longer matches`() {
        val text = "你好世界"
        assertEquals(4, voiceResumeCaret(text, 0, listOf(2, 4), "世界"))
    }

    @Test fun `positions count from the start offset of partial extracted text`() {
        assertEquals(105, voiceResumeCaret("前文你好", 101, listOf(105), "你好"))
    }

    @Test fun `text the host changed ends the dictation`() {
        assertNull(voiceResumeCaret("别的内容", 0, listOf(4), "你好"))
        assertNull(voiceResumeCaret("你好", 0, listOf(-1, 9), "你好"))
    }

    @Test fun `without dictated text only the end of the field is trusted`() {
        assertEquals(3, voiceResumeCaret("草稿。", 0, listOf(3), ""))
        assertNull(voiceResumeCaret("草稿。", 0, listOf(1), ""))
    }
}
