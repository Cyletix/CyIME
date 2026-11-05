package com.kingzcheung.xime.service

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

/** #158：微信（荣耀 Magic8）全选后光标跳到句尾或没反应。核对选上了没有，没选上补 setSelection(0, 长度)。 */
class SelectAllGuardTest {
    private var now = 1_000L
    private val logs = mutableListOf<String>()
    private val guard = SelectAllGuard(clock = { now }, log = { logs += it })

    /** 一个假输入框：[onMenu] 决定宿主收到全选后选区变成什么。 */
    private class Field(var text: String, var start: Int, var end: Int = start, var offset: Int = 0) {
        var onMenu: (Field) -> Unit = { it.start = 0; it.end = it.text.length }
        val ic: InputConnection = mock {
            on { performContextMenuAction(any()) } doAnswer { onMenu(this@Field); true }
            on { getExtractedText(any(), any()) } doAnswer {
                ExtractedText().also { e ->
                    e.text = text; e.startOffset = offset; e.selectionStart = start; e.selectionEnd = end
                }
            }
            on { setSelection(any(), any()) } doAnswer {
                start = it.getArgument(0); end = it.getArgument(1); true
            }
        }
    }

    @Test fun `host that selects all is left alone`() {
        val field = Field("你好世界", 4)
        guard.selectAll(field.ic)
        guard.selectionReported(field.ic, 0, 4)
        verify(field.ic).performContextMenuAction(android.R.id.selectAll)
        verify(field.ic, never()).setSelection(any(), any())
        assertTrue(logs.isEmpty())
    }

    @Test fun `host that ignores select all gets setSelection over the whole text`() {
        val field = Field("你好世界", 4).apply { onMenu = {} }
        guard.selectAll(field.ic)
        verify(field.ic).setSelection(0, 4)
        assertEquals(1, logs.size)
    }

    @Test fun `host that moves the caret to the end instead of selecting gets setSelection`() {
        val field = Field("你好世界", 2).apply { onMenu = { it.start = 4; it.end = 4 } }
        guard.selectAll(field.ic)
        verify(field.ic).setSelection(0, 4)
        assertEquals(0 to 4, field.start to field.end)
    }

    @Test fun `host that collapses the selection to the end soon after is repaired once`() {
        val field = Field("你好世界", 2)
        guard.selectAll(field.ic)
        guard.selectionReported(field.ic, 0, 4)
        now += 200
        field.start = 4; field.end = 4
        guard.selectionReported(field.ic, 4, 4)
        verify(field.ic, times(1)).setSelection(0, 4)
        // 补过之后再被收，不来回拉扯。
        field.start = 4; field.end = 4
        guard.selectionReported(field.ic, 4, 4)
        verify(field.ic, times(1)).setSelection(any(), any())
    }

    @Test fun `repair that the host collapses again is only logged`() {
        val field = Field("你好世界", 4).apply { onMenu = {} }
        guard.selectAll(field.ic)
        field.start = 4; field.end = 4
        guard.selectionReported(field.ic, 4, 4)
        verify(field.ic, times(1)).setSelection(any(), any())
        assertTrue(logs.last().startsWith("collapsed again"))
    }

    @Test fun `user actions after select all are not undone`() {
        // 过了时限才收成光标
        Field("你好世界", 2).let { field ->
            guard.selectAll(field.ic)
            now += SelectAllGuard.WINDOW_MS + 1
            field.start = 4; field.end = 4
            guard.selectionReported(field.ic, 4, 4)
            verify(field.ic, never()).setSelection(any(), any())
        }
        // 点到句中
        Field("你好世界", 2).let { field ->
            guard.selectAll(field.ic)
            field.start = 1; field.end = 1
            guard.selectionReported(field.ic, 1, 1)
            verify(field.ic, never()).setSelection(any(), any())
        }
        // 打字替换了全选的字，光标恰好落在原长度上
        Field("你好世界", 2).let { field ->
            guard.selectAll(field.ic)
            field.text = "再见朋友"; field.start = 4; field.end = 4
            guard.selectionReported(field.ic, 4, 4)
            verify(field.ic, never()).setSelection(any(), any())
        }
        // 做了别的编辑动作、按了实体方向键
        Field("你好世界", 2).let { field ->
            guard.selectAll(field.ic)
            guard.cancel()
            field.start = 4; field.end = 4
            guard.selectionReported(field.ic, 4, 4)
            verify(field.ic, never()).setSelection(any(), any())
        }
        // 换了输入框
        val first = Field("你好世界", 2)
        val second = Field("你好世界", 4)
        guard.selectAll(first.ic)
        guard.selectionReported(second.ic, 4, 4)
        verify(second.ic, never()).setSelection(any(), any())
    }

    @Test fun `unknown total length or empty field never calls setSelection`() {
        val partial = Field("世界", 2, offset = 10).apply { onMenu = {} }
        guard.selectAll(partial.ic)
        val empty = Field("", 0).apply { onMenu = {} }
        guard.selectAll(empty.ic)
        val unreadable = mock<InputConnection> { on { performContextMenuAction(any()) } doReturn true }
        guard.selectAll(unreadable)
        verify(partial.ic, never()).setSelection(any(), any())
        verify(empty.ic, never()).setSelection(any(), any())
        verify(unreadable, never()).setSelection(any(), any())
        assertTrue(logs.isEmpty())
    }
}
