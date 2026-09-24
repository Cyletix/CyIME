package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import org.junit.Assert.*
import org.junit.Test

class HandwritingCandidateQueueTest {
    private fun segment(vararg chars: String) = Segment(0, 1, chars.map { HandwritingCandidate(it, 0.5f) })
    @Test fun secondCharacterDoesNotReplaceOrConfirmTheFirst() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("心", "我")))
        queue.append(listOf(segment("入", "这")))
        assertEquals(listOf("心", "我"), queue.candidates)
        assertEquals("我", queue.select(1))
        assertEquals(listOf("入", "这"), queue.candidates)
        assertEquals("这", queue.select(1))
        assertEquals(0, queue.size)
    }
    @Test fun everySegmentFromOneRecognitionRemainsSelectable() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("我", "找"), segment("心", "这"), segment("入")))
        queue.deleteLast()
        assertEquals("我", queue.select(0))
        assertEquals("这", queue.select(1))
        assertNull(queue.select(0))
    }
    @Test fun explicitConfirmationFlushesAllAndCancellationFlushesNone() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("我"), segment("这")))
        assertEquals("我这", queue.confirmAll())
        assertEquals("", queue.confirmAll())
        queue.append(listOf(segment("字")))
        queue.clear()
        assertTrue(queue.candidates.isEmpty())
    }
}
