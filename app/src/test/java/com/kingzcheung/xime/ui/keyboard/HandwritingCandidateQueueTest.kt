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
    @Test fun previewShowsWritingInProgressWithoutTouchingConfirmedCharacters() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("你", "他")))
        queue.preview(listOf(segment("好", "们")))
        assertEquals("你好", queue.candidates.first())
        assertEquals(1, queue.size)
        // 撤掉预览回到已认好的字；正式结果替换预览
        queue.preview(emptyList())
        assertEquals("你", queue.candidates.first())
        queue.preview(listOf(segment("们")))
        queue.append(listOf(segment("好")))
        assertEquals("你好", queue.candidates.first())
        assertEquals(2, queue.size)
    }
    @Test fun pickingAPreviewCommitsItAndDropsTheLateResultOfTheSameInk() {
        val queue = HandwritingCandidateQueue()
        queue.preview(listOf(segment("字", "宇")))
        assertEquals("宇", queue.select(1))
        // 停顿那次识别抢在清板前回来：不再出现
        queue.append(listOf(segment("字", "宇")))
        queue.preview(listOf(segment("字")))
        assertTrue(queue.candidates.isEmpty())
        queue.newInk()
        queue.preview(listOf(segment("好")))
        assertEquals("好", queue.candidates.first())
    }
    @Test fun pickingAConfirmedCandidateDoesNotBlockTheNextResult() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("你")))
        assertEquals("你", queue.select(0))
        queue.append(listOf(segment("好")))
        assertEquals("好", queue.candidates.first())
    }
}
