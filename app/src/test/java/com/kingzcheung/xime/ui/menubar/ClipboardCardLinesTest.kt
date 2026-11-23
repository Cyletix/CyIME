package com.kingzcheung.xime.ui.menubar

import org.junit.Assert.assertEquals
import org.junit.Test

/** #300 第 8 点：卡片可以一条一行；没存过、存了别的数照旧 4 行。 */
class ClipboardCardLinesTest {
    @Test fun oneLineIsAChoiceAndDefaultStaysFour() {
        assertEquals(listOf(1, 2, 4, 8), CLIPBOARD_CARD_LINE_CHOICES)
        assertEquals(1, clipboardCardMaxLines(1))
        assertEquals(8, clipboardCardMaxLines(8))
        assertEquals(4, clipboardCardMaxLines(3))
        assertEquals(4, clipboardCardMaxLines(0))
    }
}
