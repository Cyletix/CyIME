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
        state.dismiss()
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

    @Test fun explicitlyCancellingWhileFileIsLoadingDoesNotResurrectPrompt() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        state.dismiss()
        state.prepared(key, image, now)
        assertNull(state.preview.value)
    }

    @Test fun screenshotCopiedWhileKeyboardIsHiddenRemainsAvailableOnNextOpen() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/screenshot", now)!!
        state.dismissIfPresented()
        state.prepared(key, image, now)
        state.dismissIfPresented()
        assertEquals(image, state.preview.value)
        state.presented(image.uri)
        state.dismissIfPresented()
        assertNull(state.preview.value)
        assertNull(state.beginCopy("content://source/screenshot", now))
    }

    @Test fun aFailedReadCanRetryAndAnUnseenCopyIsNotPersistedAsDismissed() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/screenshot", now)!!
        assertNull(state.lastCopyKey)
        state.failed(key)
        assertEquals(key, state.beginCopy("content://source/screenshot", now))
        state.prepared(key, image, now)
        assertEquals(image, state.preview.value)
        val restarted = ClipboardImagePreviewState(state.lastCopyKey)
        assertNotNull(restarted.beginCopy("content://source/screenshot", now))
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

    private val shot = ClipboardImage("content://media/external/images/media/42", "image/png", now, "截图")

    @Test fun screenshotWithinOneMinuteIsOfferedOnceAndExpiresAfterAMinute() {
        val state = ClipboardImagePreviewState(null)
        assertTrue(state.offerScreenshot(shot, now + 10_000))
        assertEquals(shot, state.preview.value)
        assertEquals(ClipboardImagePreviewState.SCREENSHOT_MAX_AGE_MS, state.activeMaxAgeMs)
        assertFalse("a refresh does not offer the same screenshot again", state.offerScreenshot(shot, now + 20_000))
        state.expire(now + 59_000)
        assertEquals(shot, state.preview.value)
        state.expire(now + 60_000)
        assertNull("gone after one minute", state.preview.value)
        assertFalse("never offered again after it expired", state.offerScreenshot(shot, now + 30_000))
    }

    @Test fun pastedOrDismissedScreenshotIsNotOfferedAgain() {
        val state = ClipboardImagePreviewState(null)
        assertTrue(state.offerScreenshot(shot, now + 1_000))
        state.dismiss(shot.uri)
        assertNull(state.preview.value)
        assertFalse(state.offerScreenshot(shot, now + 2_000))
    }

    @Test fun oldScreenshotsNeverPrompt() {
        val state = ClipboardImagePreviewState(null)
        assertFalse(state.offerScreenshot(shot, now + 60_000))
        assertFalse(state.offerScreenshot(shot.copy(timestamp = 0), now))
        assertNull(state.preview.value)
    }

    @Test fun copiedImageWinsAndKeepsItsLongerLifetime() {
        val state = ClipboardImagePreviewState(null)
        val key = state.beginCopy("content://source/first", now)!!
        assertFalse("a copy being prepared is not replaced by a screenshot", state.offerScreenshot(shot, now))
        state.prepared(key, image, now)
        assertEquals(image, state.preview.value)
        assertEquals(ClipboardImagePreviewState.MAX_AGE_MS, state.activeMaxAgeMs)
        val newer = shot.copy(uri = "content://media/external/images/media/43", timestamp = now + 5_000)
        assertFalse(state.offerScreenshot(newer, now + 6_000))
        assertEquals(image, state.preview.value)
    }

    @Test fun aNewerScreenshotReplacesAnOlderScreenshotHint() {
        val state = ClipboardImagePreviewState(null)
        assertTrue(state.offerScreenshot(shot, now + 1_000))
        val newer = shot.copy(uri = "content://media/external/images/media/43", timestamp = now + 5_000)
        assertTrue(state.offerScreenshot(newer, now + 6_000))
        assertEquals(newer, state.preview.value)
    }
}
