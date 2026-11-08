package com.kingzcheung.xime.audit

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.service.TrackedInputConnection
import com.kingzcheung.xime.service.XimeInputMethodService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34], application = Application::class)
class EditorDeleteAuditTest {
    @Test fun hostMovesCaretAfterTypingBeforeOrdinaryDelete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val field = EditText(context)
        val host = mock<InputConnection>()
        whenever(host.sendKeyEvent(any())).thenAnswer {
            val event = it.getArgument<KeyEvent>(0)
            field.dispatchKeyEvent(event)
        }
        val connection = TrackedInputConnection(host)
        val ime = mock<XimeInputMethodService>()
        whenever(ime.currentInputConnection).thenReturn(connection)
        whenever(ime.getSharedPreferences(any(), any())).thenAnswer { context.getSharedPreferences(it.getArgument(0), 0) }
        doCallRealMethod().whenever(ime).sendDeleteKey()
        doCallRealMethod().whenever(ime).sendDownUpKeyEvents(any())
        fun check(start: Int, end: Int, expected: String, caret: Int) {
            field.setText("甲乙丙丁")
            field.setSelection(4)
            field.setSelection(start, end)
            ime.sendDeleteKey()
            assertEquals("selection $start..$end", expected, field.text.toString())
            assertEquals(caret, field.selectionStart)
        }
        check(2, 2, "甲丙丁", 1)
        check(1, 3, "甲丁", 1)
        check(0, 0, "甲乙丙丁", 0)
        check(4, 4, "甲乙丙", 3)
    }
}
