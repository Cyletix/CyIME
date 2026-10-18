package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardImageRetentionTest {
    private fun image(id: Int, source: String = "media") =
        ClipboardImage("content://$source/$id", "image/png", id.toLong())

    @Test fun copiedAndScreenshotFeedsShareSixteenRecentPlaces() {
        val copied = (1..16).map { image(it * 2 + 90, "copied") }
        val screenshots = (1..60).map { image(it * 2 - 1) }
        val actual = retainedClipboardImages(copied, screenshots, emptySet(), emptySet())
        assertEquals(16, actual.size)
        assertEquals((copied + screenshots).sortedByDescending { it.timestamp }.take(16), actual)
        assertTrue(actual.any { it.uri.startsWith("content://copied/") })
        assertTrue(actual.any { it.uri.startsWith("content://media/") })
    }

    @Test fun everyFixedImageSurvivesInAdditionToTheRecentBudget() {
        val fixed = (1..40).map { image(it, "copied") }
        val recent = (100..159).map(::image)
        val pins = fixed.mapTo(mutableSetOf()) { "image:${it.uri}" }
        val actual = retainedClipboardImages(fixed, recent, pins, emptySet())
        assertEquals(56, actual.size)
        assertTrue(actual.containsAll(fixed))
        assertEquals((144..159).reversed().map(::image), actual.take(16))
    }

    @Test fun explicitRemovalStillHidesFixedImagesAndDoesNotConsumeARecentPlace() {
        val records = (1..30).map(::image)
        val hidden = setOf(records.last().uri, records.first().uri)
        val pins = setOf("image:${records.first().uri}")
        val actual = retainedClipboardImages(records, emptyList(), pins, hidden)
        assertEquals(16, actual.size)
        assertTrue(actual.none { it.uri in hidden })
        assertEquals((14..29).reversed().map(::image), actual)
    }

    @Test fun duplicateUrisUseOnlyOnePlaceAndPreserveCopiedIdentity() {
        val copied = image(50).copy(label = "复制图片")
        val mediaDuplicate = copied.copy(label = "截图")
        val actual = retainedClipboardImages(listOf(copied), listOf(mediaDuplicate) + (1..20).map(::image),
            emptySet(), emptySet())
        assertEquals(16, actual.size)
        assertEquals(copied, actual.first())
        assertEquals(1, actual.count { it.uri == copied.uri })
    }

    @Test fun unpinningOldImagesReappliesTheSharedBudgetWithoutMutatingHistory() {
        val original = (1..40).map(::image)
        val before = original.toList()
        val pins = original.mapTo(mutableSetOf()) { "image:${it.uri}" }
        assertEquals(40, retainedClipboardImages(original, emptyList(), pins, emptySet()).size)
        assertEquals((25..40).reversed().map(::image),
            retainedClipboardImages(original, emptyList(), emptySet(), emptySet()))
        assertEquals(before, original)
    }

    @Test fun oversizedLegacyHistoryIsBoundedWithoutDiscardingFixedRecords() {
        val original = (1..300).map(::image)
        val pins = setOf("image:${original.first().uri}", "image:${original[50].uri}", "text:300")
        val actual = retainedClipboardImages(original, emptyList(), pins, emptySet())
        assertEquals(18, actual.size)
        assertTrue(actual.containsAll(listOf(original.first(), original[50])))
        assertEquals(300, original.size)
    }

    @Test fun queryFailureAfterOptOutKeepsOnlyExplicitlyFixedAndVisibleMedia() {
        val original = (1..30).map { image(it) }
        val pins = setOf("image:${original.first().uri}", "image:${original[1].uri}")
        val actual = clipboardMediaAfterRefreshFailure(original, false, pins, setOf(original[1].uri))
        assertEquals(listOf(original.first()), actual)
    }

    @Test fun queryFailureWithFeedEnabledCannotRestoreAnExplicitlyHiddenImage() {
        val original = (1..30).map { image(it) }
        val hidden = original.last().uri
        val fallback = clipboardMediaAfterRefreshFailure(original, true, setOf("image:$hidden"), setOf(hidden))
        assertEquals(original.dropLast(1), fallback)
        val published = retainedClipboardImages(emptyList(), fallback, emptySet(), setOf(hidden))
        assertEquals((14..29).reversed().map { image(it) }, published)
    }
}
