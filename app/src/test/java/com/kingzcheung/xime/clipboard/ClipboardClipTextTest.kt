package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 短信里点链接、电话「复制」后键盘剪贴板没有（用户 11-03）：第一项文字是空的也要找到真正的内容。 */
class ClipboardClipTextTest {
    private fun text(vararg parts: ClipPart) = ClipboardManager.clipText(parts.toList())

    @Test fun plainTextIsUsedAsBefore() {
        assertEquals("你好", text(ClipPart("你好", null, null)))
    }

    @Test fun anEmptyTextFallsBackToTheLink() {
        assertEquals("https://example.com/a", text(ClipPart("", "https://example.com/a", null)))
        assertEquals("tel:10086", text(ClipPart(" ", null, "tel:10086")))
    }

    @Test fun laterItemsAreLookedAtWhenTheFirstIsEmpty() {
        assertEquals("13800138000", text(ClipPart("", null, null), ClipPart("13800138000", null, null)))
    }

    @Test fun theSystemConversionIsOnlyTheLastResort() {
        var asked = false
        assertEquals("你好", text(ClipPart("你好", null, null) { asked = true; "别的" }))
        assertEquals(false, asked)
        assertEquals("<b>链接</b>", text(ClipPart(null, null, null) { "<b>链接</b>" }))
    }

    @Test fun nothingReadableStaysEmpty() {
        assertNull(text(ClipPart(null, null, null)))
        assertNull(text())
    }
}
