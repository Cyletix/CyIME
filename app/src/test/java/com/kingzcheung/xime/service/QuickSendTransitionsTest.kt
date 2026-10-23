package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSendTransitionsTest {
    @Test fun everyOpeningTypesIntoTheFormText() {
        for (state in listOf(
            QuickSendTransitions.open(InputUIState()),
            QuickSendTransitions.open(InputUIState(), itemId = 7, text = "地址", code = "dz"),
            QuickSendTransitions.open(InputUIState(), text = "识别结果", ocr = true),
        )) {
            assertTrue(state.showQuickSendForm)
            assertEquals(InputTarget.QuickSend(code = false), state.inputTarget)
            assertEquals("换行", state.enterKeyText)
        }
        val edit = QuickSendTransitions.open(InputUIState(), itemId = 7, text = "地址", code = "dz")
        assertEquals(7L, edit.quickSendEditingItemId)
        assertEquals("dz", edit.quickSendEditingItemCode)
        assertFalse(edit.ocrDraft)
    }

    /** Closing an OCR draft or an edit leaves nothing of it for the next opening. */
    @Test fun closingLeavesTheSameStateWhateverWasOpen() {
        val fromOcr = QuickSendTransitions.close(
            QuickSendTransitions.codeFocus(QuickSendTransitions.open(InputUIState(), text = "x", ocr = true), true), "搜索")
        val fromEdit = QuickSendTransitions.close(QuickSendTransitions.open(InputUIState(), 3, "a", "b"), "搜索")
        assertEquals(fromOcr, fromEdit)
        assertEquals(InputTarget.Host, fromOcr.inputTarget)
        assertFalse(fromOcr.showQuickSendForm || fromOcr.ocrDraft)
        assertNull(fromOcr.quickSendEditingItemId)
        assertEquals("搜索", fromOcr.enterKeyText)
    }

    @Test fun focusMovesBetweenTextAndCode() {
        val open = QuickSendTransitions.open(InputUIState())
        val code = QuickSendTransitions.codeFocus(open, true)
        assertEquals(InputTarget.QuickSend(code = true), code.inputTarget)
        assertEquals("下一步", code.enterKeyText)
        val back = QuickSendTransitions.textFocus(code, true)
        assertEquals(InputTarget.QuickSend(code = false), back.inputTarget)
        assertEquals("换行", back.enterKeyText)
        // Leaving both fields keeps the form shown but types into the host again.
        val away = QuickSendTransitions.textFocus(back, false)
        assertTrue(away.showQuickSendForm)
        assertEquals(InputTarget.Host, away.inputTarget)
    }
}
