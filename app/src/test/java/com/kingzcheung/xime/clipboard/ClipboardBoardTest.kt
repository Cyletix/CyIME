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

    @Test fun roomTextPinsAndImagePinsShareOneChronologicalFrontWithoutChangingIdentities() {
        val text = listOf(ClipboardItem(1, "old fixed", timestamp = 1, isPinned = true),
            ClipboardItem(2, "latest text", timestamp = 100))
        val images = listOf(ClipboardImage("content://fixed", "image/png", 10),
            ClipboardImage("content://recent", "image/png", 90))
        val pins = clipboardPinnedKeys(text, setOf("image:content://fixed"))
        assertEquals(setOf("text:1", "image:content://fixed"), pins)
        val keys = clipboardCards(text, images, ClipboardFilter.ALL, pins).map { it.key }
        assertEquals(listOf("image:content://fixed", "text:1", "text:2", "image:content://recent"), keys)
        assertEquals("text:2", nextClipboardKey(keys, "text:1", ClipboardNavigation.RIGHT, 2, emptyList()))
        assertEquals(listOf("text:1", "text:2"),
            clipboardCards(text, images, ClipboardFilter.TEXT, pins).map { it.key })
    }

    @Test fun cancellingTextAndImagePinsImmediatelyRestoresChronologicalOrder() {
        val text = listOf(ClipboardItem(1, "old fixed", timestamp = 1, isPinned = true),
            ClipboardItem(2, "new text", timestamp = 30))
        val image = ClipboardImage("content://image", "image/png", 20)
        val oldPins = setOf("image:content://image", "text:1")
        val withoutText = clipboardPinnedKeys(text, oldPins, mapOf(1L to false))
        assertEquals(setOf("image:content://image"), withoutText)
        assertEquals(listOf("image:content://image", "text:2", "text:1"),
            clipboardCards(text, listOf(image), ClipboardFilter.ALL, withoutText).map { it.key })
        val withoutBoth = clipboardPinnedKeys(text, emptySet(), mapOf(1L to false))
        assertEquals(listOf("text:2", "image:content://image", "text:1"),
            clipboardCards(text, listOf(image), ClipboardFilter.ALL, withoutBoth).map { it.key })
        // With the old prefs marker gone, a later database refresh cannot re-pin it.
        assertTrue(clipboardPinnedKeys(text.map { it.copy(isPinned = false) }, emptySet()).isEmpty())
    }

    @Test fun pendingTextPinsWaitForTheMatchingDatabaseValueAndForgetDeletedRows() {
        val text = listOf(ClipboardItem(1, "first", isPinned = false), ClipboardItem(2, "second", isPinned = true))
        val pending = mapOf(1L to true, 2L to false, 3L to true)
        assertEquals(mapOf(1L to true, 2L to false), pendingClipboardTextPins(text, pending))
        val acknowledged = text.map { it.copy(isPinned = !it.isPinned) }
        assertTrue(pendingClipboardTextPins(acknowledged, pending).isEmpty())
    }

    @Test fun legacyTextPinMigrationIgnoresImagesMalformedKeysAndInvalidIds() {
        assertEquals(setOf(1L, 42L), legacyClipboardTextPinIds(setOf(
            "text:1", "text:42", "image:content://text:3", "text:no-id", "text:-1", "text:0", "unknown:7")))
    }
}
