package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.*

/** 审计 F06：上滑清空清整个输入框，下滑撤回原位恢复；输入框被改过就不恢复。 */
class DocumentClearTest {
    /** 只模拟光标两边和选区的最小输入框，足够检查清空与撤回的 InputConnection 调用。 */
    private class Field(var text: String, var start: Int, var end: Int = start) {
        val connection = mock<InputConnection>()
        init {
            // 和真实输入框一样，一次最多给 n 个字。
            whenever(connection.getTextBeforeCursor(any(), any())).thenAnswer {
                text.substring(maxOf(0, start - it.getArgument<Int>(0)), start)
            }
            whenever(connection.getTextAfterCursor(any(), any())).thenAnswer {
                text.substring(end, minOf(text.length, end + it.getArgument<Int>(0)))
            }
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

    private fun clear(field: Field, undoLimit: Int = CLEAR_UNDO_LIMIT, stillCurrent: () -> Boolean = { true }) =
        runBlocking {
            field.connection.readDocumentForClear(100)
                ?.let { field.connection.clearDocument(it, 100, undoLimit, stillCurrent) }
        }

    /** 2026-10-31：超过一次能读到的长度（真机 262144 字，这里 100）也要一次清完，撤回原样放回。 */
    @Test fun clearsTextLongerThanOneReadInOnePress() {
        val long = (0 until 1234).joinToString("") { "${'a' + it % 26}" } + "😀" + "尾".repeat(321)
        for (caret in listOf(0, 1, 99, 100, 101, 617, long.length - 1, long.length)) {
            val field = Field(long, caret)
            val cleared = clear(field)!!
            assertEquals("caret $caret", "", field.text)
            assertEquals(long, cleared.text)
            assertEquals("", cleared.remainingBefore); assertEquals("", cleared.remainingAfter)
            assertTrue(field.connection.restoreDocument(cleared, 100))
            assertEquals(long, field.text)
            assertEquals(caret, field.start); assertEquals(caret, field.end)
        }
    }

    @Test fun clearsALongSelectionWithLongTextAround() {
        val long = "x".repeat(450) + "选中的".repeat(10) + "y".repeat(380)
        val field = Field(long, 450, 480)
        val cleared = clear(field)!!
        assertEquals("", field.text)
        assertTrue(field.connection.restoreDocument(cleared, 100))
        assertEquals(long, field.text)
        assertEquals(450, field.start); assertEquals(480, field.end)
    }

    @Test fun hugeTextIsClearedButNotKeptForUndo() {
        val field = Field("z".repeat(1000), 500)
        assertNull(clear(field, undoLimit = 300))
        assertEquals("", field.text)
    }

    @Test fun stopsWhenTheFieldIsNoLongerCurrent() {
        val field = Field("w".repeat(1000), 500)
        val cleared = clear(field, stillCurrent = { false })!!
        assertEquals(800, field.text.length)
        assertEquals(200, cleared.text.length)
    }

    @Test fun anEditorThatIgnoresDeletesDoesNotLoopForever() {
        val field = Field("v".repeat(1000), 500)
        whenever(field.connection.deleteSurroundingText(any(), any())).thenReturn(true)
        clear(field)
        verify(field.connection, times(MAX_CLEAR_ROUNDS)).deleteSurroundingText(any(), any())
    }

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

    /** 10-31：打字中清空默认只清拼音（原行为）；开了「打字中清空整个输入框」拼音和输入框一起清；空闲时总是清输入框。 */
    @Test fun whatClearAllClears() {
        assertEquals(ClearAllScope.COMPOSITION, ClearAllScope.of(composing = true, wholeFieldWhileComposing = false))
        assertEquals(ClearAllScope.COMPOSITION_AND_FIELD, ClearAllScope.of(composing = true, wholeFieldWhileComposing = true))
        assertEquals(ClearAllScope.FIELD, ClearAllScope.of(composing = false, wholeFieldWhileComposing = false))
        assertEquals(ClearAllScope.FIELD, ClearAllScope.of(composing = false, wholeFieldWhileComposing = true))
        assertFalse(com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences().clearAllWhileComposing)
    }

    /** 直接上屏的英文（还在打字中）已经在输入框里：跟着整框一起清，撤回原样放回。 */
    @Test fun committedEnglishBeingTypedIsClearedAndRestoredWithTheField() {
        val field = Field("你好 hel", 6)
        val cleared = clear(field)!!
        assertEquals("", field.text)
        assertTrue(field.connection.restoreDocument(cleared, 100))
        assertEquals("你好 hel", field.text)
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
