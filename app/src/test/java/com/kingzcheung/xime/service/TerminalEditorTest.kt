package com.kingzcheung.xime.service

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.*

class TerminalEditorTest {
    @After fun reset() { TerminalEditor.active = false }

    // Android JVM stubs 不保存 KeyEvent 字段，直接核对真实构造参数。
    private fun sentKeys(block: () -> Unit): List<Pair<Int, Int>> {
        val events = mutableListOf<Pair<Int, Int>>()
        mockConstruction(KeyEvent::class.java) { _, construction ->
            val args = construction.arguments()
            events += (args[0] as Int) to (args[1] as Int)
        }.use { block() }
        return events
    }

    @Test fun `terminal arrows are sent as real key presses even though no text can be read`() {
        // 终端连接：读不到文字、不认选区，只收按键。
        val ic = mock<InputConnection> { on { sendKeyEvent(any()) } doReturn true }
        var steps = 0
        val events = sentKeys { TerminalEditor.sendKeys(ic, KeyEvent.KEYCODE_DPAD_UP, 3) { steps++ } }
        assertEquals(List(3) { listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_UP) }.flatten(), events)
        assertEquals(3, steps)
        verify(ic, times(6)).sendKeyEvent(any())
        verify(ic, never()).setSelection(any(), any())
        verify(ic, never()).getTextBeforeCursor(any(), any())
    }

    @Test fun `a refused key stops without reporting a step`() {
        val ic = mock<InputConnection> { on { sendKeyEvent(any()) } doReturn false }
        var steps = 0
        sentKeys { TerminalEditor.sendKeys(ic, KeyEvent.KEYCODE_DPAD_LEFT, 5) { steps++ } }
        assertEquals(0, steps)
        verify(ic, times(2)).sendKeyEvent(any())
    }

    @Test fun `editing panel actions map to terminal keys and selection variants move plainly`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, TerminalEditor.keyForEditingAction("arrow_left"))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, TerminalEditor.keyForEditingAction("select_arrow_right"))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TerminalEditor.keyForEditingAction("arrow_up"))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, TerminalEditor.keyForEditingAction("select_arrow_down"))
        for (start in listOf("home", "line_start", "select_line_start", "select_paragraph_start"))
            assertEquals(start, KeyEvent.KEYCODE_MOVE_HOME, TerminalEditor.keyForEditingAction(start))
        for (end in listOf("end", "line_end", "select_line_end", "select_paragraph_end"))
            assertEquals(end, KeyEvent.KEYCODE_MOVE_END, TerminalEditor.keyForEditingAction(end))
        for (other in listOf("undo", "redo", "copy", "cut", "paste", "select_all", "select_begin", "select_end", "select_reset"))
            assertNull(other, TerminalEditor.keyForEditingAction(other))
    }

    @Test fun `pinyin is shown in the candidate bar inside a terminal without changing the setting`() {
        val context = mock<Context>()
        TerminalEditor.active = true
        assertEquals(SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR, TerminalEditor.inputTextLocation(context))
        verifyNoInteractions(context)
    }
}
