package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kingzcheung.xime.service.EnterKeyAction.CtrlEnterKey
import com.kingzcheung.xime.service.EnterKeyAction.EditorAction
import com.kingzcheung.xime.service.EnterKeyAction.EnterKey
import com.kingzcheung.xime.service.EnterKeyAction.Newline
import com.kingzcheung.xime.service.EnterKeyAction.ShiftEnterKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** 回车键面写什么就做什么，Shift+回车换行，Ctrl+回车发送（用户 11-03：键上写换行，聊天软件里回车却发了出去）。 */
class EnterKeyActionTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    private val send = EditorInfo.IME_ACTION_SEND
    private val noEnter = EditorInfo.IME_FLAG_NO_ENTER_ACTION

    @Test fun multiLineNewlineCommitsTextInsteadOfEnterKey() {
        // 普通多行框（系统给 DONE + 回车不做动作）、聊天框（SEND + 回车不做动作）、没写动作的多行框：都上屏换行。
        assertEquals(Newline, EnterKeyAction.enter(EditorInfo.IME_ACTION_DONE or noEnter, multiLine, terminal = false))
        assertEquals(Newline, EnterKeyAction.enter(send or noEnter, multiLine, terminal = false))
        assertEquals(Newline, EnterKeyAction.enter(EditorInfo.IME_ACTION_UNSPECIFIED, multiLine, terminal = false))
        assertEquals(Newline, EnterKeyAction.enter(EditorInfo.IME_ACTION_NONE,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE, terminal = false))
        assertEquals("换行", EnterKeyAction.label(send or noEnter))
    }

    @Test fun declaredActionRunsAndMatchesTheKeyFace() {
        assertEquals(EditorAction(EditorInfo.IME_ACTION_SEARCH), EnterKeyAction.enter(EditorInfo.IME_ACTION_SEARCH, text, false))
        assertEquals("搜索", EnterKeyAction.label(EditorInfo.IME_ACTION_SEARCH))
        assertEquals("前往", EnterKeyAction.label(EditorInfo.IME_ACTION_GO))
        assertEquals("下一项", EnterKeyAction.label(EditorInfo.IME_ACTION_NEXT))
        assertEquals("完成", EnterKeyAction.label(EditorInfo.IME_ACTION_DONE))
        // 「上一项」键面写换行，回车也不往回跳。
        assertEquals("换行", EnterKeyAction.label(EditorInfo.IME_ACTION_PREVIOUS))
        assertEquals(EnterKey, EnterKeyAction.enter(EditorInfo.IME_ACTION_PREVIOUS, text, false))
    }

    @Test fun terminalsSingleLineAndRawFieldsKeepTheEnterKey() {
        assertEquals(EnterKey, EnterKeyAction.enter(EditorInfo.IME_ACTION_NONE, multiLine, terminal = true))
        assertEquals(EnterKey, EnterKeyAction.enter(noEnter, text, terminal = false))
        assertEquals(EnterKey, EnterKeyAction.enter(EditorInfo.IME_ACTION_UNSPECIFIED, InputType.TYPE_NULL, terminal = false))
        assertEquals(EnterKey, EnterKeyAction.enter(EditorInfo.IME_ACTION_UNSPECIFIED,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE, terminal = false))
    }

    @Test fun ctrlEnterSends() {
        // 用户 11-04 TIM 实测（回车发送关）：聊天框 DONE + 回车不做动作，回车键只会换行，执行「发送」才发出去。
        assertEquals(EditorAction(send), EnterKeyAction.ctrlEnter(EditorInfo.IME_ACTION_DONE or noEnter, multiLine, false))
        assertEquals(EditorAction(send), EnterKeyAction.ctrlEnter(send or noEnter, multiLine, terminal = false))
        assertEquals(EditorAction(send), EnterKeyAction.ctrlEnter(send, multiLine, terminal = false))
        assertEquals(EditorAction(send), EnterKeyAction.ctrlEnter(EditorInfo.IME_ACTION_UNSPECIFIED, multiLine, false))
        // 回车本来就执行动作的输入框：照回车。
        assertEquals(EditorAction(EditorInfo.IME_ACTION_SEARCH), EnterKeyAction.ctrlEnter(EditorInfo.IME_ACTION_SEARCH, text, false))
        assertEquals(EditorAction(EditorInfo.IME_ACTION_DONE), EnterKeyAction.ctrlEnter(EditorInfo.IME_ACTION_DONE, text, false))
        // 终端：Ctrl+回车交给终端。
        assertEquals(CtrlEnterKey, EnterKeyAction.ctrlEnter(send, multiLine, terminal = true))
    }

    /** 用户 11-04：微信开了「回车键发送消息」，普通回车照样换行、键面写换行；只有 Ctrl+回车发送。 */
    @Test fun appCannotTurnPlainEnterIntoSend() {
        assertEquals(Newline, EnterKeyAction.enter(send, multiLine, terminal = false))
        assertEquals(Newline, EnterKeyAction.enter(send, text, terminal = false))
        assertEquals("换行", EnterKeyAction.label(send))
        assertEquals(EditorAction(send), EnterKeyAction.ctrlEnter(send, text, terminal = false))
        // 终端里回车还是回车键；不收文字的发送框发回车键。
        assertEquals(EnterKey, EnterKeyAction.enter(send, multiLine, terminal = true))
        assertEquals(EnterKey, EnterKeyAction.enter(send, InputType.TYPE_CLASS_NUMBER, terminal = false))
        // 搜索、前往、下一项、完成仍由普通回车执行。
        assertEquals(EditorAction(EditorInfo.IME_ACTION_GO), EnterKeyAction.enter(EditorInfo.IME_ACTION_GO, text, false))
        assertEquals(EditorAction(EditorInfo.IME_ACTION_DONE), EnterKeyAction.enter(EditorInfo.IME_ACTION_DONE, text, false))
    }

    /** 用户 11-04：Shift+回车无条件换行，不管输入框声明了什么动作。 */
    @Test fun shiftEnterAlwaysBreaksTheLine() {
        // 回车设成发送的聊天框（多行、单行）、回车发送关的聊天框（DONE / SEND + 回车不做动作）、普通多行框：上屏 "\n"。
        assertEquals(Newline, EnterKeyAction.shiftEnter(send, multiLine, terminal = false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(send, text, terminal = false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_DONE or noEnter, multiLine, false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(send or noEnter, multiLine, false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_UNSPECIFIED, multiLine, false))
        // 多行框声明了完成、搜索：普通回车执行动作，Shift+回车换行。
        assertEquals(EditorAction(EditorInfo.IME_ACTION_DONE), EnterKeyAction.enter(EditorInfo.IME_ACTION_DONE, multiLine, false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_DONE, multiLine, false))
        assertEquals(Newline, EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_SEARCH, multiLine, false))
        // 没有换行的单行框、数字框：照普通回车。
        assertEquals(EditorAction(EditorInfo.IME_ACTION_SEARCH), EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_SEARCH, text, false))
        assertEquals(EnterKey, EnterKeyAction.shiftEnter(send, InputType.TYPE_CLASS_NUMBER, false))
        assertEquals(EnterKey, EnterKeyAction.shiftEnter(EditorInfo.IME_ACTION_UNSPECIFIED, InputType.TYPE_NULL, false))
        // 终端：Shift+回车交给终端。
        assertEquals(ShiftEnterKey, EnterKeyAction.shiftEnter(send, multiLine, terminal = true))
    }
}
