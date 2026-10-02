package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardBoardTest {
    private fun text(value: String) = ClipboardCard.Text(ClipboardItem(1, value, timestamp = 10))
    @Test fun allMergesImagesAndTextByTimestampAndPinsFirst() {
        val a = ClipboardItem(1, "old", timestamp = 1)
        val b = ClipboardItem(2, "new", timestamp = 30)
        val image = ClipboardImage("content://image", "image/png", 20)
        assertEquals(listOf("text:2", "image:content://image", "text:1"),
            clipboardCards(listOf(a, b), listOf(image), ClipboardFilter.ALL, emptySet()).map { it.key })
        assertEquals("text:1", clipboardCards(listOf(a, b), listOf(image), ClipboardFilter.ALL, setOf("text:1")).first().key)
    }
    @Test fun imageAndTextFiltersDoNotMixContentTypes() {
        val image = ClipboardImage("content://image", "image/png", 20)
        assertEquals(1, clipboardCards(listOf(ClipboardItem(text = "hello")), listOf(image, image), ClipboardFilter.IMAGE, emptySet()).size)
        assertFalse(clipboardMatches(ClipboardCard.Image(image), ClipboardFilter.TEXT))
        assertTrue(clipboardMatches(text("https://example.com"), ClipboardFilter.TEXT))
    }
    @Test fun categoriesRecognizeJapaneseAndChineseAddressesAndContacts() {
        assertTrue(clipboardMatches(text("〒860-0862 熊本県熊本市中央区黒髪7丁目763"), ClipboardFilter.ADDRESS))
        assertTrue(clipboardMatches(text("上海市南京东路100号"), ClipboardFilter.ADDRESS))
        assertTrue(clipboardMatches(text("メール: info@example.jp"), ClipboardFilter.EMAIL))
        assertTrue(clipboardMatches(text("https://example.jp/path"), ClipboardFilter.LINK))
        assertTrue(clipboardMatches(text("電話 090-1234-5678"), ClipboardFilter.PHONE))
        assertFalse(clipboardMatches(text("確認コード 123456"), ClipboardFilter.PHONE))
        assertFalse(clipboardMatches(text("こんにちは"), ClipboardFilter.ADDRESS))
    }
    @Test fun gridAdaptsToPanelWidthInsteadOfOrientation() {
        assertEquals(1, clipboardColumnCount(240f))
        assertEquals(2, clipboardColumnCount(360f))
        assertEquals(3, clipboardColumnCount(500f))
        assertEquals(5, clipboardColumnCount(1000f))
        assertEquals(5, clipboardColumnCount(1800f))
    }
}
