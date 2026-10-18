package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardBoardContentTest {
    private fun text(id: Long, timestamp: Long = id) = ClipboardCard.Text(
        ClipboardItem(id, "记录 $id", timestamp = timestamp),
    )

    @Test fun extraRecentCopiesFoldSoEveryPinStaysVisible() {
        val fixed = (1L..5L).map { text(it) }
        val recent = (101L..120L).map { text(it) }
        val cards = fixed + recent
        val pins = fixed.mapTo(mutableSetOf()) { it.key }

        val content = clipboardBoardContent(cards, pins, columns = 2, recentExpanded = false)

        assertEquals(fixed, content.pinned)
        assertEquals(recent.take(2 * ClipboardRecentRows), content.recent)
        assertEquals(20, content.recentCount)
        assertEquals("the toggle names exactly the older copies it folds", 20 - 2 * ClipboardRecentRows, content.olderCount)
        assertEquals(content.olderCount, clipboardBoardContent(cards, pins, 2, recentExpanded = true).olderCount)
        assertTrue(content.recentCollapsible)
        assertEquals(recent.take(2 * ClipboardRecentRows).map { it.key } + fixed.map { it.key }, content.visible.map { it.key })
        assertEquals("Presentation must not mutate stored history", 25, cards.size)
    }

    @Test fun recentIsNotFoldedWithoutPinsOrWhenItAlreadyFits() {
        val recent = (101L..110L).map { text(it) }
        val noPins = clipboardBoardContent(recent, emptySet(), columns = 2, recentExpanded = false)
        assertEquals(recent, noPins.recent)
        assertFalse("nothing below to make room for", noPins.recentCollapsible)

        val few = listOf(text(1), text(101), text(102))
        val fits = clipboardBoardContent(few, setOf("text:1"), columns = 2, recentExpanded = false)
        assertEquals(listOf(text(101), text(102)), fits.recent)
        assertFalse(fits.recentCollapsible)

        val onlyPins = clipboardBoardContent(listOf(text(1), text(2)), setOf("text:1", "text:2"), 2, false)
        assertEquals(listOf(text(1), text(2)), onlyPins.pinned)
        assertTrue(onlyPins.recent.isEmpty())
    }

    @Test fun expandingListsOlderCopiesAfterPinsAndCollapsingRestoresTheSameProjection() {
        val image = ClipboardCard.Image(ClipboardImage("content://image/pinned", "image/png", 10))
        val recent = (100L..104L).map { text(it) }
        val cards = listOf(image) + recent
        val pins = setOf(image.key)

        val collapsed = clipboardBoardContent(cards, pins, columns = 1, recentExpanded = false)
        val expanded = clipboardBoardContent(cards, pins, columns = 1, recentExpanded = true)
        val collapsedAgain = clipboardBoardContent(cards, pins, columns = 1, recentExpanded = false)

        assertEquals("opening older copies never pushes the pins down",
            recent.take(ClipboardRecentRows) + image + recent.drop(ClipboardRecentRows), expanded.visible)
        assertEquals(recent.take(ClipboardRecentRows) + image, collapsed.visible)
        assertEquals(collapsed, collapsedAgain)
    }

    @Test fun changingWidthOnlyChangesHowManyRecentCopiesShow() {
        val cards = listOf(text(1)) + (101L..130L).map { text(it) }
        for ((width, columns) in listOf(240f to 1, 360f to 2, 1000f to 5)) {
            val content = clipboardBoardContent(cards, setOf("text:1"), clipboardColumnCount(width), recentExpanded = false)
            assertEquals(columns * ClipboardRecentRows, content.recent.size)
            assertEquals(listOf(text(1)), content.pinned)
        }
    }

    @Test fun currentCategoryHasItsOwnPinnedAndRecentHistory() {
        val image = ClipboardImage("content://image/pinned", "image/png", 3)
        val text = listOf(ClipboardItem(1, "固定文字", timestamp = 1), ClipboardItem(2, "https://example.com", timestamp = 5))
        val pins = setOf("text:1", "image:${image.uri}")

        val images = clipboardBoardContent(clipboardCards(text, listOf(image), ClipboardFilter.IMAGE, pins), pins, 1, false)
        assertEquals(listOf("image:${image.uri}"), images.visible.map { it.key })
        assertTrue(images.recent.isEmpty())

        val links = clipboardBoardContent(clipboardCards(text, listOf(image), ClipboardFilter.LINK, pins), pins, 1, false)
        assertTrue(links.pinned.isEmpty())
        assertEquals(listOf("text:2"), links.visible.map { it.key })
    }

    @Test fun keyboardNavigationUsesOnlyTheCollapsedProjection() {
        val cards = listOf(text(1)) + (101L..110L).map { text(it) }
        val keys = clipboardBoardContent(cards, setOf("text:1"), 2, false).visible.map { it.key }

        val shown = (101L until 101L + 2 * ClipboardRecentRows).map { "text:$it" }
        assertEquals(shown + "text:1", keys)
        assertEquals("text:1", nextClipboardKey(keys, shown.last(), ClipboardNavigation.RIGHT, 2, emptyList()))
    }

    @Test fun pinPreviewLeavesMostOfTheViewportForRecentCopiesAtEveryHeight() {
        for (height in listOf(0f, 90f, 156f, 296f, 1000f)) {
            val preview = clipboardPinnedPreviewHeight(height)
            assertTrue("Pinned preview cannot exceed 35% at $height", preview <= height * .35f)
            assertTrue("Tablet preview has a readable upper bound", preview <= 112f)
            assertTrue(preview >= 0f)
        }
        assertEquals(0f, clipboardPinnedPreviewHeight(-24f), 0f)
    }
}
