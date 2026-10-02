package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardImagePreviewStateTest {
    private val now = 1_000_000L
    private val image = ClipboardImage("content://cached/first", "image/png", now)

    @Test fun newCopyWaitsForPreparationAndShowsOriginalTimestamp() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        assertNull(state.preview.value)
        state.prepared(key, image, now)
        assertEquals(image, state.preview.value)
    }

    @Test fun dismissThenRereadSameClipboardDoesNotShowAgain() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        state.prepared(key, image, now)
        state.dismiss(image.uri)
        assertNull(state.beginCopy("content://source/first", now))
        state.prepared(key, image, now)
        assertNull(state.preview.value)
    }

    @Test fun processRestartWithSavedCopyIdentityDoesNotReplayOldCopy() {
        val state = ClipboardImagePreviewState(null)
        state.beginCopy("content://source/first", now)
        val restarted = ClipboardImagePreviewState(state.lastCopyKey)
        assertNull(restarted.preview.value)
        assertNull(restarted.beginCopy("content://source/first", now))
    }

    @Test fun explicitlyCopyingSameImageAgainCanShowOneNewPrompt() {
        val state = ClipboardImagePreviewState(null)
        state.beginCopy("content://source/first", now)
        state.dismiss()
        val key = state.beginCopy("content://source/first", now + 1)!!
        val copiedAgain = image.copy(timestamp = now + 1)
        state.prepared(key, copiedAgain, now + 1)
        assertEquals(copiedAgain, state.preview.value)
        assertNull(state.beginCopy("content://source/first", now + 1))
    }

    @Test fun hidingKeyboardWhileFileIsLoadingDoesNotResurrectPrompt() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        state.dismiss()
        state.prepared(key, image, now)
        assertNull(state.preview.value)
    }

    @Test fun newerCopyWinsWhenOldFileFinishesLast() {
        val state = ClipboardImagePreviewState(null)
        val oldKey = state.beginCopy("content://source/first", now)!!
        val newKey = state.beginCopy("content://source/second", now + 1)!!
        val second = image.copy(uri = "content://cached/second", timestamp = now + 1)
        state.prepared(newKey, second, now + 1)
        state.prepared(oldKey, image, now + 1)
        state.dismiss(image.uri)
        assertEquals(second, state.preview.value)
    }

    @Test fun expiredCopyCannotProduceOrRevivePrompt() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        state.prepared(key, image, now)
        state.expire(now + ClipboardImagePreviewState.MAX_AGE_MS)
        assertNull(state.preview.value)
        state.prepared(key, image, now + ClipboardImagePreviewState.MAX_AGE_MS)
        assertNull(state.preview.value)
        val oldKey = state.beginCopy("content://source/old", 1)!!
        state.prepared(oldKey, image.copy(timestamp = 1), now)
        assertNull(state.preview.value)
    }

    @Test fun freshCopySurvivesRefreshUntilDismissedAndInvalidTimestampNeverShows() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        state.prepared(key, image, now)
        repeat(5) { state.expire(now + it) }
        assertEquals(image, state.preview.value)
        state.dismiss()
        repeat(5) { state.expire(now + it) }
        assertNull(state.preview.value)
        val invalidKey = state.beginCopy("content://source/invalid", 0)!!
        state.prepared(invalidKey, image.copy(timestamp = 0), now)
        assertNull(state.preview.value)
    }
}
