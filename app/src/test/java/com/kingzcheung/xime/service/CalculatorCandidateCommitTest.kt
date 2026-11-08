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
class CalculatorCandidateCommitTest {
    private fun field(text: String, caret: Int) = EditText(ApplicationProvider.getApplicationContext()).apply {
        setText(text)
        setSelection(caret)
    }

    @Test fun bothChoicesPreserveSurroundingTextAndLeaveCaretAfterTheChoice() {
        for ((expression, result) in listOf("1+6" to "7", "1+20000" to "20001")) {
            for (choice in listOf(result, "$expression=$result")) {
                val editor = field("前${expression}后文", 1 + expression.length)
                assertTrue(editor.onCreateInputConnection(EditorInfo())!!.commitCalculatorCandidate(expression, choice))
                assertEquals("前${choice}后文", editor.text.toString())
                assertEquals(1 + choice.length, editor.selectionEnd)
            }
        }
    }

    @Test fun movedCaretOrExistingSelectionDoesNotChangeText() {
        val editor = field("前文1+6后文", 2)
        val ic = editor.onCreateInputConnection(EditorInfo())!!
        assertFalse(ic.commitCalculatorCandidate("1+6", "7"))
        editor.setSelection(2, 5)
        assertFalse(ic.commitCalculatorCandidate("1+6", "1+6=7"))
        assertEquals("前文1+6后文", editor.text.toString())
        assertEquals(2, editor.selectionStart)
        assertEquals(5, editor.selectionEnd)
    }

    @Test fun rejectedWriteLeavesExpressionAndOriginalCaret() {
        val editor = field("前1+6后", 4)
        val ic = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = false
        }
        assertFalse(ic.commitCalculatorCandidate("1+6", "7"))
        assertEquals("前1+6后", editor.text.toString())
        assertEquals(4, editor.selectionStart)
        assertEquals(4, editor.selectionEnd)
    }

    @Test fun editorCanReportItsSelectionThroughExtractedTextAlone() {
        val editor = field("前1+6后", 4)
        val ic = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
            override fun getSelectedText(flags: Int): CharSequence? = null
        }
        assertTrue(ic.commitCalculatorCandidate("1+6", "7"))
        assertEquals("前7后", editor.text.toString())
        assertEquals(2, editor.selectionEnd)
    }
}
