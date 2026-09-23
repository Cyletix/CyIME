package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import com.kingzcheung.xime.handwriting.StrokePoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HandwritingInputSessionTest {
    private class Fixture(scope: TestScope, slow: Boolean = false) {
        val events = mutableListOf<String>()
        val windows = mutableListOf<Int>()
        val session = HandwritingInputSession(scope, { 1000L }, { strokes ->
            windows += strokes.size
            if (slow) withContext(NonCancellable) { delay(2000) }
            listOf(Segment(0, strokes.size, listOf(HandwritingCandidate("字", 1f))))
        }, { events += "begin" }, { events += "result" }, { events += it })
        fun stroke(): Int = session.begin(StrokePoint(10f, 10f, 0)).also {
            session.move(it, StrokePoint(30f, 10f, 20)); session.end(it)
        }
    }

    @Test fun emptyButtonsNeverBecomeWritingOrRecognition() = runTest {
        val f = Fixture(this)
        val keys = listOf("delete", "space", "number", "symbol", "ime_switch", "，", "。", "enter")
        keys.forEach(f.session::press)
        advanceUntilIdle()
        assertEquals(keys, f.events)
        assertTrue(f.windows.isEmpty())
        assertFalse(f.session.hasInk)
    }

    @Test fun touchDownCancelsWaitingAndReleaseRestartsTheFullDelay() = runTest {
        val f = Fixture(this)
        f.stroke(); advanceTimeBy(850)
        val token = f.session.begin(StrokePoint(20f, 20f))
        advanceTimeBy(1500); runCurrent()
        assertTrue(f.windows.isEmpty())
        f.session.end(token)
        advanceTimeBy(999); runCurrent(); assertTrue(f.windows.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(2), f.windows)
        assertFalse(f.session.hasInk)
    }

    @Test fun deleteClearsAllUnrecognizedInkWithoutDeletingHostText() = runTest {
        val f = Fixture(this)
        f.stroke(); f.stroke()
        f.session.press("delete"); advanceUntilIdle()
        assertFalse(f.session.hasInk)
        assertEquals(listOf("begin"), f.events)
        f.session.press("delete")
        assertEquals(listOf("begin", "delete"), f.events)
    }

    @Test fun switchDropsEvenANonCancellableLateResult() = runTest {
        for (key in listOf("number", "symbol", "ime_switch")) {
            val f = Fixture(this, slow = true)
            f.stroke(); advanceTimeBy(1000); runCurrent()
            assertEquals(HandwritingPhase.RECOGNIZING, f.session.phase)
            f.session.press(key); advanceUntilIdle()
            assertEquals(listOf("begin", key), f.events)
            assertFalse(f.session.hasInk)
        }
    }

    @Test fun spaceAndPunctuationFlushInkBeforeTheirAction() = runTest {
        for (key in listOf("space", "enter", "，", "。")) {
            val f = Fixture(this)
            f.stroke(); f.session.press(key); runCurrent()
            assertEquals(listOf("begin", "result", key), f.events)
            assertFalse(f.session.hasInk)
        }
    }

    @Test fun resumingDuringInferenceDiscardsOldResultAndRecognizesCompleteInk() = runTest {
        val f = Fixture(this, slow = true)
        f.stroke(); advanceTimeBy(1000); runCurrent()
        f.stroke(); advanceUntilIdle()
        assertEquals(listOf(1, 2), f.windows)
        assertEquals(listOf("begin", "result"), f.events)
        assertFalse(f.session.hasInk)
    }

    @Test fun deletingDuringSpaceFlushCancelsTheQueuedSpaceToo() = runTest {
        val f = Fixture(this, slow = true)
        f.stroke(); f.session.press("space"); runCurrent()
        f.session.press("delete"); advanceUntilIdle()
        assertEquals(listOf("begin"), f.events)
        assertFalse(f.session.hasInk)
    }

    @Test fun aDotIsAStrokeButCancelledTouchesDoNotCommit() = runTest {
        val f = Fixture(this)
        f.session.end(f.session.begin(StrokePoint(10f, 10f)), cancelled = true)
        advanceUntilIdle(); assertTrue(f.windows.isEmpty())
        f.session.end(f.session.begin(StrokePoint(10f, 10f)))
        advanceUntilIdle(); assertEquals(listOf(1), f.windows)
    }

    @Test fun nextCharacterHasAnIndependentWindowAndDisposalDropsPendingWork() = runTest {
        val f = Fixture(this)
        f.stroke(); advanceUntilIdle()
        f.stroke(); advanceUntilIdle()
        assertEquals(listOf(1, 1), f.windows)
        assertEquals(listOf("begin", "result", "begin", "result"), f.events)
        f.stroke(); f.session.clear(); advanceUntilIdle()
        assertEquals(2, f.windows.size)
    }
    @Test fun repeatedKeysDuringRecognitionAreExecutedInOrderWithoutDroppingEarlierTaps() = runTest {
        val f = Fixture(this, slow = true)
        f.stroke(); f.session.press("space"); runCurrent()
        f.session.press("space"); f.session.press("，")
        advanceUntilIdle()
        assertEquals(listOf("begin", "result", "space", "space", "，"), f.events)
        assertEquals(listOf(1), f.windows)
    }

}
