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
}
