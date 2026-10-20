package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardImageRetentionTest {
    private val limit = RECENT_CLIPBOARD_IMAGE_LIMIT
    private fun image(id: Int, source: String = "media") =
        ClipboardImage("content://$source/$id", "image/png", id.toLong())

    @Test fun recentBudgetIsFiftyImages() = assertEquals(50, limit)

    @Test fun copiedAndScreenshotFeedsShareTheRecentPlaces() {
        // Interleaved timestamps: the newest places mix both feeds.
        val copied = (1..limit).map { image(1000 + it * 2, "copied") }
        val screenshots = (1..limit * 3).map { image(1000 + limit * 2 + 1 - it * 2) }
        val actual = retainedClipboardImages(copied, screenshots, emptySet(), emptySet())
        assertEquals(limit, actual.size)
        assertEquals((copied + screenshots).sortedByDescending { it.timestamp }.take(limit), actual)
        assertTrue(actual.any { it.uri.startsWith("content://copied/") })
        assertTrue(actual.any { it.uri.startsWith("content://media/") })
    }

    @Test fun everyFixedImageSurvivesInAdditionToTheRecentBudget() {
        val fixed = (1..40).map { image(it, "copied") }
        val recent = (100 until 100 + limit + 10).map(::image)
        val pins = fixed.mapTo(mutableSetOf()) { "image:${it.uri}" }
        val actual = retainedClipboardImages(fixed, recent, pins, emptySet())
        assertEquals(40 + limit, actual.size)
        assertTrue(actual.containsAll(fixed))
        assertEquals(recent.takeLast(limit).reversed(), actual.take(limit))
    }

    @Test fun explicitRemovalStillHidesFixedImagesAndDoesNotConsumeARecentPlace() {
        val records = (1..limit + 14).map(::image)
        val hidden = setOf(records.last().uri, records.first().uri)
        val pins = setOf("image:${records.first().uri}")
        val actual = retainedClipboardImages(records, emptyList(), pins, hidden)
        assertEquals(limit, actual.size)
        assertTrue(actual.none { it.uri in hidden })
        assertEquals((14..limit + 13).reversed().map(::image), actual)
    }

    @Test fun duplicateUrisUseOnlyOnePlaceAndPreserveCopiedIdentity() {
        val copied = image(limit * 10).copy(label = "复制图片")
        val mediaDuplicate = copied.copy(label = "截图")
        val actual = retainedClipboardImages(listOf(copied), listOf(mediaDuplicate) + (1..limit + 4).map(::image),
            emptySet(), emptySet())
        assertEquals(limit, actual.size)
        assertEquals(copied, actual.first())
        assertEquals(1, actual.count { it.uri == copied.uri })
    }

    @Test fun unpinningOldImagesReappliesTheSharedBudgetWithoutMutatingHistory() {
        val original = (1..limit + 24).map(::image)
        val before = original.toList()
        val pins = original.mapTo(mutableSetOf()) { "image:${it.uri}" }
        assertEquals(limit + 24, retainedClipboardImages(original, emptyList(), pins, emptySet()).size)
        assertEquals((25..limit + 24).reversed().map(::image),
            retainedClipboardImages(original, emptyList(), emptySet(), emptySet()))
        assertEquals(before, original)
    }

    @Test fun oversizedLegacyHistoryIsBoundedWithoutDiscardingFixedRecords() {
        val original = (1..300).map(::image)
        val pins = setOf("image:${original.first().uri}", "image:${original[50].uri}", "text:300")
        val actual = retainedClipboardImages(original, emptyList(), pins, emptySet())
        assertEquals(limit + 2, actual.size)
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
        val original = (1..limit + 14).map { image(it) }
        val hidden = original.last().uri
        val fallback = clipboardMediaAfterRefreshFailure(original, true, setOf("image:$hidden"), setOf(hidden))
        assertEquals(original.dropLast(1), fallback)
        val published = retainedClipboardImages(emptyList(), fallback, emptySet(), setOf(hidden))
        assertEquals((14..limit + 13).reversed().map { image(it) }, published)
    }

    @Test fun cacheOverBudgetEvictsOldestUnfixedCopiesOnly() {
        val mb = 1024L * 1024
        val files = (1..12).map { CachedImageFile("f$it.png", 50 * mb, it.toLong()) }
        // 600 MB in 12 files; the two oldest are fixed and must stay.
        val evicted = clipboardImageEvictions(files, setOf("f1.png", "f2.png"))
        assertEquals(listOf("f3.png", "f4.png"), evicted)
        assertTrue(files.filterNot { it.name in evicted }.sumOf { it.bytes } <= CLIPBOARD_IMAGE_CACHE_BYTES)
    }

    @Test fun cacheWithinBudgetOrOnlyFixedFilesEvictsNothing() {
        val mb = 1024L * 1024
        assertTrue(clipboardImageEvictions(listOf(CachedImageFile("a", 100 * mb, 1)), emptySet()).isEmpty())
        val fixedOnly = (1..3).map { CachedImageFile("p$it", 300 * mb, it.toLong()) }
        assertTrue(clipboardImageEvictions(fixedOnly, fixedOnly.mapTo(mutableSetOf()) { it.name }).isEmpty())
    }
}
