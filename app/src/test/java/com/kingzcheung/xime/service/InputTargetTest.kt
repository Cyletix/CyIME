package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTargetTest {
    @Test fun noInternalFocusTypesIntoTheHost() {
        assertEquals(InputTarget.Host, InputUIState().inputTarget)
        assertFalse(InputUIState().inputTarget.isInternal)
        // A form that is shown but not focused does not take typed text.
        assertEquals(InputTarget.Host, InputUIState(showQuickSendForm = true).inputTarget)
    }

    @Test fun quickSendFocusPicksTextOrCodeField() {
        assertEquals(InputTarget.QuickSend(code = false), InputUIState(quickSendFormFocused = true).inputTarget)
        assertEquals(InputTarget.QuickSend(code = true),
            InputUIState(quickSendFormFocused = true, quickSendCodeFocused = true).inputTarget)
        // The code flag alone never routed to the code field.
        assertEquals(InputTarget.Host, InputUIState(quickSendCodeFocused = true).inputTarget)
    }

    @Test fun toolPanelChannelIsSearchWhileSearchIsOpen() {
        assertEquals(InputTarget.ToolPanel, InputUIState(toolPanelInputFocused = true).inputTarget)
        assertEquals(InputTarget.ClipboardSearch,
            InputUIState(toolPanelInputFocused = true, clipboardSearchVisible = true).inputTarget)
        assertTrue(InputTarget.ClipboardSearch.isInternal)
    }

    /** 剪贴板搜索开着时搜索框丢了视图焦点（工具栏切换布局，10-30），按键仍进搜索框，不漏到后面的应用。 */
    @Test fun clipboardSearchKeepsKeysWhenItsFieldLosesViewFocus() {
        val searching = InputUIState(toolPanelInputFocused = true, clipboardSearchVisible = true)
        assertTrue(searching.toolPanelFocusAfter(viewFocused = false))
        assertEquals(InputTarget.ClipboardSearch,
            searching.copy(toolPanelInputFocused = searching.toolPanelFocusAfter(false)).inputTarget)
        // A plugin tool panel still lets go when its field loses focus.
        assertFalse(InputUIState(toolPanelInputFocused = true).toolPanelFocusAfter(viewFocused = false))
        assertTrue(InputUIState().toolPanelFocusAfter(viewFocused = true))
    }

    /** Commit and cursor paths always preferred quick send when both flags were set. */
    @Test fun quickSendWinsOverTheToolPanelChannel() {
        assertEquals(InputTarget.QuickSend(code = false), InputUIState(
            quickSendFormFocused = true, toolPanelInputFocused = true, clipboardSearchVisible = true).inputTarget)
    }

    @Test fun hostHasNoInternalEditor() {
        assertEquals(null, InputTarget.Host.editText())
    }

    /** 新增固定等键盘自己的输入框里点删除（11-03）：和应用里的退格一样删一个完整的字，有选区删选区。 */
    @Test fun internalBackspaceDeletesOneWholeCharacterOrTheSelection() {
        assertEquals(1 until 2, internalBackspaceRange("ab", 2, 2))
        assertEquals(null, internalBackspaceRange("ab", 0, 0))
        assertEquals(null, internalBackspaceRange("", -1, -1))
        // 选区：正着拖、反着拖都删整段。
        assertEquals(1 until 3, internalBackspaceRange("abcd", 1, 3))
        assertEquals(1 until 3, internalBackspaceRange("abcd", 3, 1))
        // 表情占两个 UTF-16 单元，以前只删半个，留下乱码。
        val face = "a\uD83D\uDE00"
        assertEquals(1 until 3, internalBackspaceRange(face, 3, 3))
        // 国旗是两个区域字母，删一下删整面旗。
        val flag = "x\uD83C\uDDE8\uD83C\uDDF3"
        assertEquals(1 until 5, internalBackspaceRange(flag, 5, 5))
        // 长草稿只看光标前一小段，结果照样落在整段里的正确位置。
        val long = "字".repeat(200) + "好"
        assertEquals(200 until 201, internalBackspaceRange(long, 201, 201))
    }
}
