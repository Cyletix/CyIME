package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kingzcheung.xime.service.EnterKeyAction.EditorAction
import com.kingzcheung.xime.service.EnterKeyAction.EnterKey
import com.kingzcheung.xime.service.EnterKeyAction.Newline
import org.junit.Assert.assertEquals
import org.junit.Test

/** 回车键面写什么就做什么（用户 11-03：键上写换行，聊天软件里回车却发了出去）。 */
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
        assertEquals(EditorAction(send), EnterKeyAction.enter(send, multiLine, terminal = false))
        assertEquals("发送", EnterKeyAction.label(send))
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
}
