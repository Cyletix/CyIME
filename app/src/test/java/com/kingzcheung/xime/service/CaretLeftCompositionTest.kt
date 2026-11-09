package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼音写在输入框里时把光标点到编码以外，要结束组词（11-08 红米 K70：光标挪到中间按删除，删的是末尾）。
 * 例：正文「你好」后面打了 nihao，编码在 2..7。
 */
class CaretLeftCompositionTest {
    @Test fun caretInsideOrAtEitherEdgeOfTheCodeKeepsComposing() {
        for (caret in 2..7) assertFalse("caret $caret", inlineCaretOutsideSpan(caret, caret, 2, 7))
    }

    @Test fun caretTappedIntoEarlierTextLeavesTheComposition() {
        assertTrue(inlineCaretOutsideSpan(1, 1, 2, 7))
        assertTrue(inlineCaretOutsideSpan(0, 0, 2, 7))
    }

    @Test fun caretAfterTheCodeLeavesTheComposition() {
        // 编码后面还有字（在句中打拼音），点到后面的字。
        assertTrue(inlineCaretOutsideSpan(9, 9, 2, 7))
    }

    @Test fun aSelectionReachingOutsideTheCodeLeavesTheComposition() {
        assertTrue(inlineCaretOutsideSpan(0, 4, 2, 7))
        assertTrue(inlineCaretOutsideSpan(5, 9, 2, 7))
        assertFalse(inlineCaretOutsideSpan(3, 5, 2, 7))
    }

    @Test fun noReportedCodeSpanIsNotTreatedAsLeaving() {
        // 宿主没报组合区（-1）或报空区时不判断，交给原有的找回编码流程。
        assertFalse(inlineCaretOutsideSpan(0, 0, -1, -1))
        assertFalse(inlineCaretOutsideSpan(0, 0, 3, 3))
        assertFalse(inlineCaretOutsideSpan(-1, -1, 2, 7))
    }
}
