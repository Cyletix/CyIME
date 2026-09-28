package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import org.junit.Assert.*
import org.junit.Test

class HandwritingCandidateQueueTest {
    private fun segment(vararg chars: String) = Segment(0, 1, chars.mapIndexed { i, s -> HandwritingCandidate(s, 0.9f / (i + 1)) })
    @Test fun sequentialWritingOffersWholePhraseAndCommitsOnce() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("你", "他")))
        queue.append(listOf(segment("好", "们")))
        assertEquals("你好", queue.candidates.first())
        assertTrue(queue.candidates.containsAll(listOf("你们", "他好", "他们")))
        assertEquals("他们", queue.select(queue.candidates.indexOf("他们")))
        assertTrue(queue.candidates.isEmpty())
        assertEquals(0, queue.size)
        assertNull(queue.select(0))
    }
    @Test fun deleteOnlyRemovesLastUnconfirmedCharacter() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("我", "找"), segment("们", "这"), segment("好")))
        queue.deleteLast()
        assertEquals("我们", queue.candidates.first())
        assertEquals("我这", queue.select(queue.candidates.indexOf("我这")))
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
    @Test fun invalidSelectionPreservesWritingAndSearchRemainsBounded() {
        val queue = HandwritingCandidateQueue()
        queue.append(List(10) { segment("中", "国", "文", "字") })
        assertNull(queue.select(-1))
        assertEquals(10, queue.size)
        assertTrue(queue.candidates.size <= 20)
        assertTrue(queue.candidates.all { it.length == 10 })
    }
}
