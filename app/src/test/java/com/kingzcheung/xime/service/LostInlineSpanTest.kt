package com.kingzcheung.xime.service

import android.app.Application
import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34], application = Application::class)
class LostInlineSpanTest {
    private class Host : BaseInputConnection(View(ApplicationProvider.getApplicationContext()), true) {
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            text = editable.toString()
            selectionStart = Selection.getSelectionStart(editable)
            selectionEnd = Selection.getSelectionEnd(editable)
        }
    }

    private val code = "wo'hao'da'jia'hao"

    private fun compose(host: Host): InlineCompositionTracker {
        host.commitText("前后", 1)
        host.setSelection(1, 1)
        return InlineCompositionTracker().apply {
            selectionReported(1, 1, -1, -1)
            host.setComposingText(code, 1)
            shown(code)
            selectionReported(1 + code.length, 1 + code.length, 1, 1 + code.length)
            host.finishComposingText()
        }
    }

    @Test fun tapInsideFrozenPinyinThenTypeAndPickLeavesOneWordBetweenTheOriginalCharacters() {
        val host = Host()
        val tracker = compose(host)
        host.setSelection(5, 5)
        assertEquals(DetachedComposition(1, 1 + code.length), tracker.restoreLostSpan(host, 5, 5, -1, -1))
        assertEquals(5, Selection.getSelectionStart(host.editable))
        host.setComposingText("$code'a", 1)
        assertEquals("前$code'a后", host.editable.toString())
        host.commitText("大家好", 1)
        assertEquals("前大家好后", host.editable.toString())
    }

    @Test fun staleCallbackEditedTextOutsideCaretAndSelectedTextAreNeverAdopted() {
        for (scenario in listOf("stale", "changed", "outside", "range")) {
            val host = Host()
            val tracker = compose(host)
            var reported = 5
            var end = 5
            when (scenario) {
                "stale" -> host.setSelection(6, 6)
                "changed" -> { host.editable!!.replace(1, 2, "改"); host.setSelection(5, 5) }
                "outside" -> { reported = 0; end = 0; host.setSelection(0, 0) }
                "range" -> { end = 6; host.setSelection(5, 6) }
            }
            assertNull(scenario, tracker.restoreLostSpan(host, reported, end, -1, -1))
            assertEquals(scenario, -1, BaseInputConnection.getComposingSpanStart(host.editable!!))
        }
    }
}
