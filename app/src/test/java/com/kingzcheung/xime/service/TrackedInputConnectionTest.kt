package com.kingzcheung.xime.service

import android.app.Application
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

/**
 * 九键在 B 站这类主线程忙的宿主里打字：每个新词的第一个数字键、每次上屏、每次上屏回执都要问一次光标前文字，
 * 每次同步等宿主约 130 ms。记住已知文字后，稳定打字时不再问宿主，答案和宿主自己回答的一样。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class TrackedInputConnectionTest {
    /** 一个普通输入框：照收上屏的字，退格删一个字。 */
    private class Host(initial: String = "") {
        val text = StringBuilder(initial)
        var caret = initial.length
        var reads = 0
        val ic: InputConnection = mock {
            on { getTextBeforeCursor(any(), any()) } doAnswer {
                reads++
                text.substring(0, caret).takeLast(it.getArgument<Int>(0))
            }
            on { commitText(any(), any()) } doAnswer {
                val inserted = it.getArgument<CharSequence>(0).toString()
                text.insert(caret, inserted)
                caret += inserted.length
                true
            }
            on { sendKeyEvent(any()) } doAnswer {
                val event = it.getArgument<KeyEvent>(0)
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL && caret > 0) {
                    text.deleteCharAt(caret - 1)
                    caret--
                }
                true
            }
            on { finishComposingText() } doReturn true
            on { setSelection(any(), any()) } doAnswer { caret = it.getArgument(0); true }
        }
        fun truth(n: Int) = text.substring(0, caret).takeLast(n)
    }

    private fun tracked(host: Host) = TrackedInputConnection(host.ic).also {
        it.memory.started(host.caret, host.caret, enabled = true)
    }

    /** 九键一个词：首个数字键读 16 字左文，选词上屏后读 25 字联想上下文，宿主报回选区后再核对一次。 */
    private fun typeWord(ic: TrackedInputConnection, host: Host, word: String) {
        assertEquals(host.truth(16), ic.getTextBeforeCursor(16, 0).toString())
        ic.commitText(word, 1)
        ic.finishComposingText()
        assertEquals(host.truth(25), ic.getTextBeforeCursor(25, 0).toString())
        ic.memory.selectionReported(host.caret, host.caret, -1, -1)
        assertEquals(host.truth(25), ic.getTextBeforeCursor(25, 0).toString())
    }

    @Test fun `steady typing stops asking the editor after the first word`() {
        val host = Host("评论：")
        val ic = tracked(host)
        typeWord(ic, host, "这个")
        val readsAfterFirstWord = host.reads
        repeat(10) { typeWord(ic, host, "视频真好看") }
        assertEquals(readsAfterFirstWord, host.reads)
        assertEquals("评论：这个" + "视频真好看".repeat(10), host.text.toString())
    }

    @Test fun `backspace makes the next word ask the editor again and get the real text`() {
        val host = Host()
        val ic = tracked(host)
        typeWord(ic, host, "你好")
        typeWord(ic, host, "世界")
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        val reads = host.reads
        assertEquals("你好世", ic.getTextBeforeCursor(16, 0).toString())
        assertEquals(reads + 1, host.reads)
    }

    @Test fun `tapping elsewhere in the text makes the next read ask the editor`() {
        val host = Host()
        val ic = tracked(host)
        typeWord(ic, host, "你好")
        typeWord(ic, host, "世界")
        host.caret = 1 // 用户在宿主里点到第一个字后面
        ic.memory.selectionReported(1, 1, -1, -1)
        assertEquals("你", ic.getTextBeforeCursor(16, 0).toString())
    }

    @Test fun `own cursor moves forget the text`() {
        val host = Host()
        val ic = tracked(host)
        typeWord(ic, host, "你好世界")
        ic.setSelection(2, 2)
        assertEquals("你好", ic.getTextBeforeCursor(16, 0).toString())
    }

    @Test fun `styled reads always go to the editor`() {
        val host = Host("abc")
        val ic = tracked(host)
        typeWord(ic, host, "d")
        val reads = host.reads
        ic.getTextBeforeCursor(2, InputConnection.GET_TEXT_WITH_STYLES)
        assertEquals(reads + 1, host.reads)
    }

    @Test fun `password fields are never answered from memory`() {
        val host = Host()
        val ic = TrackedInputConnection(host.ic).also { it.memory.started(0, 0, enabled = false) }
        typeWord(ic, host, "abc")
        val reads = host.reads
        typeWord(ic, host, "def")
        assertEquals(reads + 3, host.reads)
    }
}
