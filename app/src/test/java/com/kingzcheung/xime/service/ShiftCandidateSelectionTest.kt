package com.kingzcheung.xime.service

import android.view.KeyEvent
import com.kingzcheung.xime.gloss.CandidateGlossary
import com.kingzcheung.xime.gloss.GlossTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** 物理键盘按住 Shift 选候选：Shift+空格 / Shift+数字的接管条件和上屏内容。 */
class ShiftCandidateSelectionTest {

    private val table = GlossTable.fromBuffer(ByteBuffer.wrap("# 说明\n你好\thello\n电脑\tcomputer\n".toByteArray()))
    private val englishFor: (String) -> String? = { CandidateGlossary.englishFor(table, it) }

    private fun takesOver(keyCode: Int, shift: Boolean = true, chinese: Boolean = true,
                          gloss: Boolean = true, composing: Boolean = true) =
        takesOverShiftedCandidateKey(keyCode, shift, chinese, gloss, composing)

    @Test
    fun `shift space while composing chinese is a candidate selection, not a typed space`() {
        assertTrue(takesOver(KeyEvent.KEYCODE_SPACE))
        // 词典没加载也接管：否则会先上中文再补一个空格。
        assertTrue(takesOver(KeyEvent.KEYCODE_SPACE, gloss = false))
    }

    @Test
    fun `shift space commits the English when there is one, otherwise the Chinese, never with a space`() {
        assertEquals("hello", shiftSelectionEnglish("你好", englishFor))
        assertNull(shiftSelectionEnglish("拟好", englishFor))   // 没有英文：照常选中文候选
        assertFalse(shiftSelectionEnglish("你好", englishFor)!!.endsWith(" "))
    }

    @Test
    fun `shift digit picks a candidate only while the glossary is loaded`() {
        assertTrue(takesOver(KeyEvent.KEYCODE_1))
        assertFalse(takesOver(KeyEvent.KEYCODE_1, gloss = false))   // 平时 Shift+1 仍是输入 !
    }

    @Test
    fun `keys without shift, outside chinese composition, keep their old behavior`() {
        assertFalse(takesOver(KeyEvent.KEYCODE_SPACE, shift = false))
        assertFalse(takesOver(KeyEvent.KEYCODE_1, shift = false))
        assertFalse(takesOver(KeyEvent.KEYCODE_SPACE, chinese = false))
        assertFalse(takesOver(KeyEvent.KEYCODE_SPACE, composing = false))
        assertFalse(takesOver(KeyEvent.KEYCODE_A))
    }
}
