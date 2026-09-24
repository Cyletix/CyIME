package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestPredictionTest {
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
