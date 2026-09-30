package com.kingzcheung.xime.rime

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class AutomaticPreparationTest {
    @Test fun failuresRetryInOrderUntilReady() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val messages = mutableListOf<String>()
        retryPreparation(messages::add, { waits.add(it) }) {
            attempts++
            if (attempts == 1) error("故障注入")
            attempts == 3
        }
        assertEquals(3, attempts)
        assertEquals(listOf(3000L, 10000L), waits)
        assertTrue(messages.any { "自动重试" in it })
    }
    @Test fun cancellationDoesNotRetryOrPretendReady() = runBlocking {
        var waits = 0
        try {
            retryPreparation({}, { waits++ }) { throw CancellationException("closed") }
            fail("cancel must propagate")
        } catch (_: CancellationException) { assertEquals(0, waits) }
    }
}
