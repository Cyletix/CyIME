package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

/** 审计 F06：上滑清空清整个输入框，下滑撤回原位恢复；输入框被改过就不恢复。 */
class DocumentClearTest {
    /** 只模拟光标两边和选区的最小输入框，足够检查清空与撤回的 InputConnection 调用。 */
    private class Field(var text: String, var start: Int, var end: Int = start) {
        val connection = mock<InputConnection>()
        init {
            whenever(connection.getTextBeforeCursor(any(), any())).thenAnswer { text.substring(0, start) }
            whenever(connection.getTextAfterCursor(any(), any())).thenAnswer { text.substring(end) }
            whenever(connection.getSelectedText(any())).thenAnswer { text.substring(start, end).ifEmpty { null } }
            whenever(connection.commitText(any(), any())).thenAnswer {
                val inserted = it.getArgument<CharSequence>(0).toString()
                text = text.substring(0, start) + inserted + text.substring(end)
                start += inserted.length; end = start; true
            }
            whenever(connection.deleteSurroundingText(any(), any())).thenAnswer {
                val before = it.getArgument<Int>(0); val after = it.getArgument<Int>(1)
                text = text.substring(0, start - before) + text.substring(start, end) + text.substring(end + after)
                start -= before; end -= before; true
            }
            whenever(connection.setSelection(any(), any())).thenAnswer {
                start = it.getArgument(0); end = it.getArgument(1); true
            }
        }
    }

    private fun clear(field: Field): ClearedDocument? =
        field.connection.readDocumentForClear(100)?.let { field.connection.clearDocument(it, 100) }

    @Test fun clearsTextOnBothSidesOfTheCursor() {
        val field = Field("abcdef", 3)
        assertNotNull(clear(field))
        assertEquals("", field.text)
    }

    @Test fun clearsASelectionTooAndUndoRestoresItInPlace() {
        val field = Field("abcdef", 2, 4)
        val cleared = clear(field)!!
        assertEquals("", field.text)
        assertEquals("abcdef", cleared.text)
        assertTrue(field.connection.restoreDocument(cleared, 100))
        assertEquals("abcdef", field.text)
        assertEquals(2, field.start); assertEquals(4, field.end)
    }

    @Test fun undoPutsTheCaretBackWhereItWas() {
        for (caret in 0..6) {
            val field = Field("abcdef", caret)
            val cleared = clear(field)!!
            assertTrue(field.connection.restoreDocument(cleared, 100))
            assertEquals("abcdef", field.text)
            assertEquals(caret, field.start); assertEquals(caret, field.end)
        }
    }

    @Test fun undoDoesNothingAfterTheFieldChanged() {
        val field = Field("abcdef", 3)
        val cleared = clear(field)!!
        field.connection.commitText("新", 1)
        assertFalse(field.connection.restoreDocument(cleared, 100))
        assertEquals("新", field.text)
    }

    @Test fun anEmptyFieldRecordsNothing() {
        assertNull(clear(Field("", 0)))
    }

    @Test fun restoreChecksWhatWasLeftAroundTheCaret() {
        val cleared = ClearedDocument("ab", "", "cd", remainingBefore = "前", remainingAfter = "")
        assertTrue(cleared.canRestoreInto("前", ""))
        assertFalse(cleared.canRestoreInto("", ""))
        assertFalse(cleared.canRestoreInto(null, ""))
        assertEquals(2..2, cleared.restoredSelection)
    }
}
