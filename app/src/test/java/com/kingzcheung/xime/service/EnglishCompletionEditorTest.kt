package com.kingzcheung.xime.service

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34], application = Application::class)
class EnglishCompletionEditorTest {
    private val editor = EditText(ApplicationProvider.getApplicationContext())

    @Test fun completionWorksWhenEditorReportsSuccessWithoutDeletingThePrefix() {
        val host = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true
        }
        editor.setText("a")
        editor.setSelection(1)
        host.beginBatchEdit()
        host.deleteSurroundingText(1, 0)
        host.commitText("and", 1)
        host.endBatchEdit()
        assertEquals("aand", editor.text.toString())

        for ((prefix, choice) in listOf("a" to "and", "t" to "the", "An" to "And ")) {
            editor.setText("前${prefix}后")
            editor.setSelection(1 + prefix.length)
            assertTrue(host.commitEnglishCompletion(prefix, choice))
            assertEquals("前${choice}后", editor.text.toString())
            assertEquals(1 + choice.length, editor.selectionEnd)
            host.commitText("x", 1)
            assertEquals("前${choice}x后", editor.text.toString())
        }
    }

    @Test fun spellingCorrectionsRequireTheEditorToSelectTheOriginalWord() {
        editor.setText("前dont后")
        editor.setSelection(5)
        val host = editor.onCreateInputConnection(EditorInfo())!!
        val ignoresSelection = object : InputConnectionWrapper(host, false) {
            override fun setSelection(start: Int, end: Int) = true
        }
        assertFalse(ignoresSelection.commitEnglishCompletion("dont", "don't"))
        assertEquals("前dont后", editor.text.toString())
        assertTrue(host.commitEnglishCompletion("dont", "don't"))
        assertEquals("前don't后", editor.text.toString())
        assertEquals(6, editor.selectionEnd)
    }

    @Test fun movedCaretSelectionAndRejectedWritePreserveTheDraft() {
        editor.setText("at")
        val attempts = mutableListOf<String>()
        val connection = TrackedInputConnection(object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                attempts += text.toString()
                return false
            }
        })
        editor.setSelection(2)
        connection.memory.started(2, 2, true)
        assertEquals("t", connection.getTextBeforeCursor(1, 0).toString())
        assertFalse(connection.commitEnglishCompletion("t", "the"))
        assertEquals("at", editor.text.toString())
        assertEquals(2, editor.selectionEnd)

        editor.setSelection(1)
        assertFalse(connection.commitEnglishCompletion("t", "the"))
        editor.setSelection(1, 2)
        assertFalse(connection.commitEnglishCompletion("a", "and"))
        assertEquals(listOf("he"), attempts)
        assertEquals("at", editor.text.toString())
        assertEquals(1, editor.selectionStart)
        assertEquals(2, editor.selectionEnd)
    }
}
