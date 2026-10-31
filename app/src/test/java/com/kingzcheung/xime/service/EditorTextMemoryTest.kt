package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorTextMemoryTest {
    /** 已经读过一次、宿主也回执过一次上屏位置的输入框：光标在 "前文你好" 后面（位置 4）。 */
    private fun confirmed(): EditorTextMemory = EditorTextMemory().apply {
        started(2, 2, enabled = true)
        learn("前文", 64)
        committed("你好", 1, accepted = true)
        selectionReported(4, 4, -1, -1)
    }

    @Test fun `nothing is known before the first real read`() {
        val memory = EditorTextMemory().apply { started(0, 0, enabled = true) }
        assertNull(memory.textBefore(1))
    }

    @Test fun `a real read is answered again without asking the editor`() {
        val memory = EditorTextMemory().apply { started(5, 5, enabled = true) }
        memory.learn("hello", 64)
        assertEquals("llo", memory.textBefore(3))
        // 读到的比要的短：这就是输入框开头，要多少都是这些。
        assertEquals("hello", memory.textBefore(25))
    }

    @Test fun `a read without a known caret is not kept`() {
        val memory = EditorTextMemory().apply { started(0, 3, enabled = true) }
        memory.learn("abc", 64)
        assertNull(memory.textBefore(1))
    }

    @Test fun `own commits are only trusted once the editor has echoed one`() {
        val memory = EditorTextMemory().apply { started(2, 2, enabled = true) }
        memory.learn("前文", 64)
        memory.committed("你好", 1, accepted = true)
        // 宿主还没回执过：这个输入框会不会真的照收不知道（终端、限长输入框），照旧去读。
        assertNull(memory.textBefore(4))
        memory.selectionReported(4, 4, -1, -1)
        assertEquals("前文你好", memory.textBefore(4))
        memory.committed("世界", 1, accepted = true)
        // 回执过一次以后，刚上屏、回执还在路上的这段也算数。
        assertEquals("你好世界", memory.textBefore(4))
        memory.selectionReported(6, 6, -1, -1)
        assertEquals("你好世界", memory.textBefore(4))
    }

    @Test fun `late echoes of earlier own commits keep the text`() {
        val memory = confirmed()
        memory.committed("我", 1, accepted = true)
        memory.committed("们", 1, accepted = true)
        memory.selectionReported(5, 5, -1, -1)
        memory.selectionReported(6, 6, -1, -1)
        assertEquals("前文你好我们", memory.textBefore(6))
    }

    @Test fun `a selection report that is not one of ours forgets the text`() {
        val memory = confirmed()
        memory.selectionReported(1, 1, -1, -1) // 点了别处
        assertNull(memory.textBefore(1))
        // 宿主报来的位置记下，下一次真读到的就能接着用。
        memory.learn("前", 64)
        assertEquals("前", memory.textBefore(1))
    }

    @Test fun `a selection or a composition in the report forgets the text`() {
        val selected = confirmed().apply { selectionReported(2, 4, -1, -1) }
        assertNull(selected.textBefore(1))
        val composing = confirmed().apply { selectionReported(6, 6, 4, 6) }
        assertNull(composing.textBefore(1))
    }

    @Test fun `any other edit forgets the text and the caret`() {
        val memory = confirmed()
        memory.edited()
        assertNull(memory.textBefore(1))
        memory.learn("前文你", 64)
        // 光标在哪不知道（退格的回执还没到），读到的不记。
        assertNull(memory.textBefore(1))
    }

    @Test fun `a rejected or caret-moving commit forgets the text`() {
        val rejected = confirmed().apply { committed("x", 1, accepted = false) }
        assertNull(rejected.textBefore(1))
        val caretInside = confirmed().apply { committed("（）", 0, accepted = true) }
        assertNull(caretInside.textBefore(1))
    }

    @Test fun `the editor changing a commit shows up as a foreign echo`() {
        val memory = confirmed()
        memory.committed("一二三", 1, accepted = true)
        // 限长输入框只收了一个字：回执的位置对不上。
        memory.selectionReported(5, 5, -1, -1)
        assertNull(memory.textBefore(1))
    }

    @Test fun `composing text is never answered from memory`() {
        val memory = confirmed()
        memory.composingSet("ni", accepted = true)
        assertNull(memory.textBefore(1))
        memory.committed("你", 1, accepted = true)
        // 上屏替换了组合区：位置要等宿主报来。
        assertNull(memory.textBefore(1))
        memory.selectionReported(5, 5, -1, -1)
        memory.learn("前文你好你", 64)
        assertEquals("你", memory.textBefore(1))
    }

    @Test fun `clearing or finishing an absent composition changes nothing`() {
        val memory = confirmed()
        memory.composingSet("", accepted = true)
        memory.composingFinished()
        assertEquals("你好", memory.textBefore(2))
    }

    @Test fun `marking existing text as a composition forgets the text`() {
        val memory = confirmed().apply { composingRegion(2, 4) }
        memory.selectionReported(4, 4, -1, -1)
        memory.learn("前文你好", 64)
        assertNull(memory.textBefore(1))
    }

    @Test fun `only the last characters are kept and longer requests ask the editor`() {
        val memory = EditorTextMemory(capacity = 4).apply { started(6, 6, enabled = true) }
        memory.learn("abcdef", 4)
        assertEquals("cdef", memory.textBefore(4))
        assertNull(memory.textBefore(5))
        val short = EditorTextMemory(capacity = 4).apply { started(2, 2, enabled = true) }
        short.learn("ab", 4)
        assertEquals("ab", short.textBefore(4))
        // 超出记的长度就不再算“到开头了”。
        assertNull(short.textBefore(5))
    }

    @Test fun `secret editors keep nothing`() {
        val memory = EditorTextMemory().apply { started(0, 0, enabled = false) }
        memory.learn("", 64)
        memory.committed("pw", 1, accepted = true)
        assertNull(memory.textBefore(0))
    }

    @Test fun `a new field starts over`() {
        val memory = confirmed()
        memory.started(0, 0, enabled = true)
        assertNull(memory.textBefore(1))
        memory.learn("", 64)
        memory.committed("a", 1, accepted = true)
        // 新输入框还没回执过上屏位置。
        assertNull(memory.textBefore(1))
    }
}
