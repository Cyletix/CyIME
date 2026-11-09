package com.kingzcheung.xime.service

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
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
class DictationRestartEditorTest {
    private val editor = EditText(ApplicationProvider.getApplicationContext()).apply {
        setText("前听写后")
        setSelection(0)
    }
    private val host = editor.onCreateInputConnection(EditorInfo())!!

    @Test fun restoredPreviewIsReplacedExactlyOnceInTheMiddleOfADraft() {
        assertEquals(3, host.restoreDictationPosition(listOf(3), "听写", true))
        host.commitText("听写完成", 1)
        assertEquals("前听写完成后", editor.text.toString())
        assertEquals(5, editor.selectionEnd)
    }

    @Test fun refusesARejectedOrIgnoredCursorMoveAndARejectedComposingRegion() {
        for (ack in listOf(false, true)) {
            val ignoresMove = object : InputConnectionWrapper(host, false) {
                override fun setSelection(start: Int, end: Int) = ack
                override fun setComposingRegion(start: Int, end: Int) = error("caret still at zero")
            }
            assertNull(ignoresMove.restoreDictationPosition(listOf(3), "听写", true))
            assertEquals(0, editor.selectionEnd)
        }
        val rejectsRegion = object : InputConnectionWrapper(host, false) {
            override fun setComposingRegion(start: Int, end: Int) = false
        }
        assertNull(rejectsRegion.restoreDictationPosition(listOf(3), "听写", true))
        assertEquals("前听写后", editor.text.toString())
    }

    @Test fun preservesNewCaretSelectionAndChangedDraft() {
        for ((start, end) in listOf(1 to 1, 1 to 3)) {
            editor.setSelection(start, end)
            assertNull(host.restoreDictationPosition(listOf(3), "听写", false))
            assertEquals(start, editor.selectionStart)
            assertEquals(end, editor.selectionEnd)
        }
        editor.setSelection(0)
        assertNull(host.restoreDictationPosition(listOf(3), "别处", false))
        assertEquals(0, editor.selectionEnd)
    }

    @Test @Config(sdk = [34]) fun editorProvidingOnlySurroundingTextCanResume() {
        val limited = object : InputConnectionWrapper(host, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = null
        }
        assertEquals(3, limited.restoreDictationPosition(listOf(3), "听写", false))
        limited.commitText("完成", 1)
        assertEquals("前听写完成后", editor.text.toString())
    }
}
