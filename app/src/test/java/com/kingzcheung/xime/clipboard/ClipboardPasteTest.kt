package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardPasteTest {
    private val pinned = listOf(ClipboardItem(1, "示例密码 Abc-123", isPinned = true))

    /** A fixed record, also when picked from search results, is committed privately (no learning, no plugin event). */
    @Test fun pinnedTextIsCommittedPrivately() {
        assertEquals(ClipboardPasteMode.PRIVATE, clipboardPasteMode("示例密码 Abc-123", pinned))
    }

    @Test fun ordinaryHistoryIsAnOrdinaryPaste() {
        assertEquals(ClipboardPasteMode.PASTE, clipboardPasteMode("最近复制的文字", pinned))
        assertEquals(ClipboardPasteMode.PASTE, clipboardPasteMode("示例密码 Abc-123", emptyList()))
    }
}
