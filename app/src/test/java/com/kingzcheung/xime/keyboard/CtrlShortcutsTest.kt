package com.kingzcheung.xime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CtrlShortcutsTest {
    @Test
    fun selectAllCopyCutPasteUseEditPanelActions() {
        assertEquals("select_all", CtrlShortcuts.editingAction('a'))
        assertEquals("select_all", CtrlShortcuts.editingAction('A'))
        assertEquals("copy", CtrlShortcuts.editingAction('c'))
        assertEquals("cut", CtrlShortcuts.editingAction('x'))
        assertEquals("paste", CtrlShortcuts.editingAction('v'))
    }

    @Test
    fun otherLettersStayCtrlKeyEvents() {
        // 撤销/重做与编辑面板一样仍是 Ctrl+Z/Y 按键；其余字母交给应用自己处理。
        for (letter in listOf('z', 'y', 's', 'f', 'w', 't', 'b', 'q')) assertNull(CtrlShortcuts.editingAction(letter))
    }

    @Test
    fun enterHasItsOwnCommand() {
        assertEquals(CtrlShortcuts.ENTER_COMMAND, CtrlShortcuts.command(CtrlShortcuts.ENTER))
        // ctrl:enter 不是字母命令，不会被当成 Ctrl+E 之类发出去。
        assertNull(CtrlShortcuts.letterOf(CtrlShortcuts.ENTER_COMMAND))
        assertEquals("ctrl:c", CtrlShortcuts.command('C'))
    }
}
