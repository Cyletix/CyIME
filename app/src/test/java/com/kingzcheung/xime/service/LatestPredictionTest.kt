package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestPredictionTest {
    @Test fun pendingPredictionIsVisibleDuringDebounceAndConsumedOnce() = runTest {
        val output = mutableListOf<String>()
        val worker = LatestPrediction(backgroundScope, { listOf(it) }, { output += it })
        val requests = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            worker.pendingRequest.collect { requests += it }
        }
        assertEquals(listOf(0L), requests)
        worker.submit("正在等待")
        val cancelledRequest = worker.pendingRequest.value
        assertTrue(cancelledRequest != 0L)
        assertTrue(worker.isPending)
        assertTrue(worker.invalidate())
        assertEquals(0L, worker.pendingRequest.value)
        assertFalse(worker.invalidate())
        advanceTimeBy(300); runCurrent()
        assertTrue(output.isEmpty())
        worker.submit("新请求")
        val completedRequest = worker.pendingRequest.value
        assertTrue(completedRequest != 0L && completedRequest != cancelledRequest)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf("新请求"), output)
        assertFalse(worker.isPending)
        assertEquals(listOf(0L, cancelledRequest, 0L, completedRequest, 0L), requests)
    }

    @Test fun emptyResultCompletesObservablePendingRequestAfterDelivery() = runTest {
        val output = mutableListOf<List<String>>()
        lateinit var worker: LatestPrediction
        worker = LatestPrediction(backgroundScope, { emptyList() }, {
            assertTrue(worker.isPending)
            output += it
        }, { testScheduler.currentTime })
        val requests = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            worker.pendingRequest.collect { requests += it }
        }

        worker.submit("没有联想")
        val request = worker.pendingRequest.value
        assertTrue(request != 0L)
        advanceTimeBy(160); runCurrent()

        assertEquals(listOf(emptyList<String>()), output)
        assertEquals(listOf(0L, request, 0L), requests)
        assertFalse(worker.isPending)
    }

    @Test fun staleNonCancellableCompletionKeepsNewerRequestPending() = runTest {
        val oldResult = CompletableDeferred<Unit>()
        val newResult = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val output = mutableListOf<String>()
        val worker = LatestPrediction(backgroundScope, {
            calls += it
            if (it == "old") withContext(NonCancellable) { oldResult.await() }
            else newResult.await()
            listOf(it)
        }, { output += it }, { testScheduler.currentTime })

        worker.submit("old")
        advanceTimeBy(160); runCurrent()
        assertEquals(listOf("old"), calls)
        val oldRequest = worker.pendingRequest.value
        worker.submit("new")
        val newRequest = worker.pendingRequest.value
        assertTrue(newRequest != 0L && newRequest != oldRequest)

        oldResult.complete(Unit)
        runCurrent()
        assertEquals(newRequest, worker.pendingRequest.value)
        assertTrue(worker.isPending)
        assertTrue(output.isEmpty())

        advanceTimeBy(160); runCurrent()
        assertEquals(listOf("old", "new"), calls)
        assertEquals(newRequest, worker.pendingRequest.value)
        newResult.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), output)
        assertEquals(0L, worker.pendingRequest.value)
        assertFalse(worker.isPending)
    }

    @Test fun burstRunsOnlyLatestAndInvalidationDropsResult() = runTest {
        val calls = mutableListOf<String>(); val output = mutableListOf<String>()
        val worker = LatestPrediction(backgroundScope, { calls += it; delay(20); listOf(it) }, { output += it }, { testScheduler.currentTime })
        repeat(100) { worker.submit("$it") }
        advanceTimeBy(200); runCurrent()
        assertEquals(listOf("99"), calls); assertEquals(listOf("99"), output)
        worker.submit("obsolete"); advanceTimeBy(165); worker.invalidate(); advanceTimeBy(100); runCurrent()
        assertEquals(listOf("99"), output)
    }
    @Test fun slowNativeCallDoesNotBuildQueueOrPublishStaleContext() = runTest {
        val calls = mutableListOf<String>(); val output = mutableListOf<String>()
        var active = 0; var maximum = 0
        val worker = LatestPrediction(backgroundScope, {
            calls += it; active++; maximum = maxOf(maximum, active)
            withContext(NonCancellable) { delay(600) }; active--; listOf(it)
        }, { output += it }, { testScheduler.currentTime })
        worker.submit("first"); advanceTimeBy(200)
        repeat(100) { worker.submit("next$it") }
        advanceTimeBy(2500); runCurrent()
        assertEquals(listOf("first", "next99"), calls)
        assertEquals(listOf("next99"), output); assertEquals(1, maximum)
    }
}
