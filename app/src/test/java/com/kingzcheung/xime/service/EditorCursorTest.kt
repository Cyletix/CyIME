package com.kingzcheung.xime.service

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.*

class EditorCursorTest {
    // Android JVM stubs 不保存 KeyEvent 字段，直接核对真实构造参数和发送次数。
    private fun assertCancelKeyPair(ic: InputConnection, key: Int, cancel: () -> Unit) {
        val events = mutableListOf<List<Int>>()
        mockConstruction(KeyEvent::class.java) { _, construction ->
            val args = construction.arguments()
            events += listOf(args[2] as Int, args[3] as Int, args[5] as Int)
        }.use { cancel() }
        assertEquals(listOf(listOf(KeyEvent.ACTION_DOWN, key, 0), listOf(KeyEvent.ACTION_UP, key, 0)), events)
        verify(ic, times(2)).sendKeyEvent(any())
    }

    private fun editor(start: Int, end: Int = start, offset: Int = 0, text: String = "abcd", normalized: Boolean = false): InputConnection {
        val extracted = ExtractedText().apply {
            this.text = text
            startOffset = offset
            selectionStart = start
            selectionEnd = end
        }
        return mock<InputConnection> {
            on { getExtractedText(any(), any()) } doReturn extracted
            on { getTextBeforeCursor(any(), any()) } doAnswer {
                text.take(minOf(extracted.selectionStart, extracted.selectionEnd)).takeLast(it.getArgument(0))
            }
            on { getTextAfterCursor(any(), any()) } doAnswer {
                text.drop(maxOf(extracted.selectionStart, extracted.selectionEnd)).take(it.getArgument(0))
            }
            on { setSelection(any(), any()) } doAnswer {
                val anchor = it.getArgument<Int>(0) - offset
                val active = it.getArgument<Int>(1) - offset
                extracted.selectionStart = if (normalized) minOf(anchor, active) else anchor
                extracted.selectionEnd = if (normalized) maxOf(anchor, active) else active
                true
            }
        }
    }

    @Test fun `vertical arrows delegate visual wrapping to the editor`() {
        val up = editor(4, text = "abcdefghijklmnop")
        assertCancelKeyPair(up, KeyEvent.KEYCODE_DPAD_UP) { EditorCursor().moveVertical(up, -1) }
        val down = editor(4, text = "abcdefghijklmnop")
        assertCancelKeyPair(down, KeyEvent.KEYCODE_DPAD_DOWN) { EditorCursor().moveVertical(down, 1) }
    }

    @Test fun `vertical arrows never escape the text at either boundary`() {
        val first = editor(0)
        val last = editor(4)
        EditorCursor().moveVertical(first, -20)
        EditorCursor().moveVertical(last, 20)
        verify(first, never()).sendKeyEvent(any())
        verify(last, never()).sendKeyEvent(any())
    }

    @Test fun `selection uses absolute offset and preserves emoji`() {
        val ic = editor(1, offset = 200, text = "a😀b")
        EditorCursor().move(ic, 1)
        verify(ic).setSelection(203, 203)
    }

    @Test fun `left collapses selection before moving another character`() {
        val ic = editor(1, 3)
        EditorCursor().move(ic, -1)
        verify(ic).setSelection(1, 1)
    }

    @Test fun `repeated selection uses active endpoint and keeps anchor`() {
        val ic = editor(1)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, 1, true)
        cursor.move(ic, 1, true)
        cursor.move(ic, -1, true)
        verify(ic).setSelection(1, 3)
        verify(ic, times(2)).setSelection(1, 2)
    }

    @Test fun `unsupported selection falls back to paired key events`() {
        val ic = editor(1)
        doReturn(false).whenever(ic).setSelection(any(), any())
        EditorCursor().move(ic, 2)
        verify(ic, times(4)).sendKeyEvent(any())
    }

    @Test fun `new input connection cannot reuse selection anchor`() {
        val first = editor(1)
        val second = editor(2, offset = 100)
        val cursor = EditorCursor()
        cursor.beginSelection(first)
        cursor.move(second, 1, true)
        verify(second).setSelection(102, 103)
    }

    @Test fun `unicode movement clamps at edges without splitting surrogate pair`() {
        assertEquals(1, offsetByCharacters("a😀b", 3, -1))
        assertEquals(4, offsetByCharacters("a😀b", 0, 20))
        assertEquals(0, offsetByCharacters("a😀b", 4, -20))
    }

    @Test fun `normalized reverse selection expands beyond one character`() {
        val ic = editor(3, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        repeat(3) { cursor.move(ic, -1, true) }
        inOrder(ic) {
            verify(ic).setSelection(3, 2)
            verify(ic).setSelection(3, 1)
            verify(ic).setSelection(3, 0)
        }
    }

    @Test fun `normalized selection can shrink cross anchor and expand the other way`() {
        val ic = editor(3, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -2, true)
        repeat(3) { cursor.move(ic, 1, true) }
        inOrder(ic) {
            verify(ic).setSelection(3, 1)
            verify(ic).setSelection(3, 2)
            verify(ic).setSelection(3, 3)
            verify(ic).setSelection(3, 4)
        }
    }

    @Test fun `reverse selection keeps absolute anchor and whole unicode codepoints`() {
        val ic = editor(4, offset = 200, text = "a😀b", normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -1, true)
        cursor.move(ic, -1, true)
        verify(ic).setSelection(204, 203)
        verify(ic).setSelection(204, 201)
    }

    @Test fun `cancel reverse selection collapses to the active smaller endpoint`() {
        val ic = editor(3, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -2, true)
        cursor.endSelection(ic)
        verify(ic).setSelection(1, 1)
        cursor.beginSelection(ic)
        cursor.move(ic, 1, true)
        verify(ic).setSelection(1, 2)
    }

    @Test fun `cancel forward selection collapses to the active larger endpoint`() {
        val ic = editor(1, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, 2, true)
        cursor.endSelection(ic)
        verify(ic).setSelection(3, 3)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `cancel selection without extracted text uses selected text confirmation`() {
        val ic = mock<InputConnection>()
        whenever(ic.getSelectedText(0)).thenReturn("selected")
        assertCancelKeyPair(ic, KeyEvent.KEYCODE_DPAD_RIGHT) { EditorCursor().endSelection(ic) }
        verify(ic, never()).setSelection(any(), any())
    }

    @Test fun `cancel refused forward selection sends unshifted right pair`() {
        val ic = editor(1, 3)
        doReturn(false).whenever(ic).setSelection(any(), any())
        assertCancelKeyPair(ic, KeyEvent.KEYCODE_DPAD_RIGHT) { EditorCursor().endSelection(ic) }
        verify(ic).setSelection(3, 3)
    }

    @Test fun `cancel refused normalized reverse selection sends unshifted left pair`() {
        val ic = editor(3, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -2, true)
        doReturn(false).whenever(ic).setSelection(any(), any())
        clearInvocations(ic)
        assertCancelKeyPair(ic, KeyEvent.KEYCODE_DPAD_LEFT) { cursor.endSelection(ic) }
        verify(ic).setSelection(1, 1)
    }

    @Test fun `cancel without known selection never sends a direction`() {
        val ic = mock<InputConnection>()
        EditorCursor().endSelection(ic)
        verify(ic, never()).setSelection(any(), any())
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `cancel rejected collapsed selection does not move the cursor`() {
        val ic = editor(2)
        doReturn(false).whenever(ic).setSelection(any(), any())
        whenever(ic.getSelectedText(0)).thenReturn("")
        EditorCursor().endSelection(ic)
        verify(ic).setSelection(2, 2)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `session reset clears direction without changing the editor selection`() {
        val ic = editor(3, normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -1, true)
        clearInvocations(ic)
        cursor.resetSelection()
        verify(ic, never()).setSelection(any(), any())
        cursor.move(ic, 1, true)
        verify(ic).setSelection(2, 4)
    }

    @Test fun `paragraph boundaries use newline rather than document endpoints`() {
        val ic = editor(6, text = "one\nabcd\nlast")
        val cursor = EditorCursor()
        cursor.moveToParagraphBoundary(ic, false)
        cursor.moveToParagraphBoundary(ic, true)
        verify(ic).setSelection(4, 4)
        verify(ic).setSelection(8, 8)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `selecting paragraph start and end keeps the same anchor`() {
        val ic = editor(6, text = "one\nabcd\nlast", normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.moveToParagraphBoundary(ic, false, true)
        cursor.moveToParagraphBoundary(ic, true, true)
        verify(ic).setSelection(6, 4)
        verify(ic).setSelection(6, 8)
    }

    @Test fun `final paragraph end is verified using editor text after cursor`() {
        val ic = editor(10, text = "one\nabcd\nlast")
        EditorCursor().moveToParagraphBoundary(ic, true)
        verify(ic).setSelection(13, 13)
    }

    @Test fun `clipped extracted text is extended to the real paragraph end`() {
        val full = "prefix\n" + "x".repeat(1000) + "\nlast"
        val ic = editor(100, offset = 407, text = full.substring(407, 807))
        whenever(ic.getTextAfterCursor(any(), any())).thenAnswer { full.substring(507).take(it.getArgument(0)) }
        EditorCursor().moveToParagraphBoundary(ic, true)
        verify(ic).getTextAfterCursor(256, 0)
        verify(ic).getTextAfterCursor(512, 0)
        verify(ic).setSelection(1007, 1007)
    }

    @Test fun `clipped extracted text is extended to the real paragraph start`() {
        val full = "prefix\n" + "x".repeat(1000) + "\nlast"
        val ic = editor(100, offset = 407, text = full.substring(407, 807))
        whenever(ic.getTextBeforeCursor(any(), any())).thenAnswer { full.substring(0, 507).takeLast(it.getArgument(0)) }
        EditorCursor().moveToParagraphBoundary(ic, false)
        verify(ic).setSelection(7, 7)
    }

    @Test fun `paragraph delimiters preserve CRLF unicode separators and empty paragraphs`() {
        val text = "a😀\r\nb\u2028\u2029c"
        assertEquals(0, paragraphBoundary(text, 2, false))
        assertEquals(3, paragraphBoundary(text, 2, true))
        assertEquals(3, paragraphBoundary(text, 4, true))
        assertEquals(5, paragraphBoundary(text, 5, false))
        assertEquals(6, paragraphBoundary(text, 5, true))
        assertEquals(7, paragraphBoundary(text, 7, false))
        assertEquals(7, paragraphBoundary(text, 7, true))
        assertEquals(8, paragraphBoundary(text, 9, false))
    }
    @Test fun `overshooting document edges clamps without emitting focus navigation`() {
        val ic = editor(2, text = "a😀b")
        val cursor = EditorCursor()
        cursor.move(ic, 20)
        repeat(3) { cursor.move(ic, 1) }
        cursor.move(ic, -20)
        repeat(3) { cursor.move(ic, -1) }
        verify(ic, times(4)).setSelection(4, 4)
        verify(ic, times(4)).setSelection(0, 0)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `empty document never emits horizontal focus keys`() {
        val ic = editor(0, text = "")
        val cursor = EditorCursor()
        cursor.move(ic, -1)
        cursor.move(ic, 1)
        verify(ic, times(2)).setSelection(0, 0)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `boundary guard still works if editor refuses setSelection`() {
        val ic = editor(4)
        doReturn(false).whenever(ic).setSelection(any(), any())
        EditorCursor().move(ic, 5)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `boundary guard works without extracted text`() {
        val ic = mock<InputConnection>()
        whenever(ic.getTextBeforeCursor(1, 0)).thenReturn("")
        whenever(ic.getTextAfterCursor(1, 0)).thenReturn("")
        EditorCursor().move(ic, -1)
        EditorCursor().move(ic, 1)
        verify(ic, never()).sendKeyEvent(any())
    }

    @Test fun `clipped horizontal snapshot does not stop at fragment edge`() {
        val ic = editor(2, offset = 2, text = "cd")
        whenever(ic.getTextAfterCursor(any(), any())).thenReturn("😀xy")
        EditorCursor().move(ic, 2)
        verify(ic).setSelection(7, 7)
        verify(ic, never()).sendKeyEvent(any())
        val reverse = editor(0, offset = 4, text = "xy")
        whenever(reverse.getTextBeforeCursor(any(), any())).thenReturn("ab😀")
        EditorCursor().move(reverse, -2)
        verify(reverse).setSelection(1, 1)
        verify(reverse, never()).sendKeyEvent(any())
    }

    @Test fun `reverse selection overshoot stays anchored and clamps both ends`() {
        val ic = editor(2, text = "abcd", normalized = true)
        val cursor = EditorCursor()
        cursor.beginSelection(ic)
        cursor.move(ic, -20, true)
        cursor.move(ic, 20, true)
        verify(ic).setSelection(2, 0)
        verify(ic).setSelection(2, 4)
        verify(ic, never()).sendKeyEvent(any())
    }

}
