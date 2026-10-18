package com.kingzcheung.xime.speech.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionTranscriptTest {
    /** ASRInput's caption replay: five endpoints and five late finals produce five lines, never rewound. */
    @Test fun `late results for a closed line are rejected and line count is stable`() {
        val transcript = CaptionTranscript()
        for (id in 1L..5L) {
            assertNotNull(transcript.update(id, "预览$id", id * 1000, 0, 0, false, ""))
            assertNotNull(transcript.update(id, "定稿$id", id * 1000, id * 1000 + 900, 0, true, ""))
        }
        for (id in 1L..5L) {
            assertNull("closed line $id must ignore a late preview", transcript.update(id, "迟到$id", 0, 0, 0, false, ""))
            assertNull("closed line $id must ignore a second final", transcript.update(id, "再定稿$id", 0, 0, 0, true, ""))
        }
        assertEquals(5, transcript.size)
        assertEquals(listOf("定稿1", "定稿2", "定稿3", "定稿4", "定稿5"), transcript.entries().map { it.text })
    }

    @Test fun `speaker labels may still change after the text is closed`() {
        val transcript = CaptionTranscript()
        transcript.update(7, "明天来接你", 0, 2000, 0, true, "")
        transcript.assign(7, 2, false)
        assertEquals(2, transcript[7]!!.speakerId)
        assertFalse(transcript[7]!!.confident)
        assertTrue(transcript.reassign(7, 3))
        assertEquals(3, transcript[7]!!.speakerId)
        assertTrue(transcript[7]!!.confident)
        assertEquals("定稿文字不因改派而变", "明天来接你", transcript[7]!!.text)
    }

    @Test fun `merging speakers moves every line of the merged speaker`() {
        val transcript = CaptionTranscript()
        for (id in 1L..4L) { transcript.update(id, "句$id", 0, 0, 0, true, ""); transcript.assign(id, if (id % 2 == 0L) 2 else 1, true) }
        assertEquals(2, transcript.mergeSpeakers(2, 1))
        assertTrue(transcript.entries().all { it.speakerId == 1 })
    }

    @Test fun `a speaker label for an unknown id creates a placeholder that text can fill later`() {
        val transcript = CaptionTranscript()
        transcript.assign(9, 1, true)
        assertNotNull(transcript.update(9, "后到的文字", 0, 0, 0, true, ""))
        assertEquals(1, transcript[9]!!.speakerId)
    }
}
