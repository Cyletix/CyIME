package com.kingzcheung.xime.rime

import com.kingzcheung.xime.service.InputCommandOwner
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InputCommandQueueTest {
    @Test fun nextKeyWaitsThroughSuspendedCommitAndNestedCleanup() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        try {
            queue.submit {
                events += "select"
                entered.complete(Unit)
                release.await()
                withContext(Dispatchers.IO) {
                    queue.execute { events += "commit" }
                }
                events += "learn-and-clear"
            }
            withTimeout(3000) { entered.await() }
            val next = queue.submit { events += "next-key" }
            assertFalse(next.isCompleted)
            release.complete(Unit)
            withTimeout(3000) { next.join() }
            assertEquals(listOf("select", "commit", "learn-and-clear", "next-key"), events)
        } finally { queue.close() }
    }

    @Test fun expiredCommandDoesNotCancelFollowingEditor() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        var executed = false
        try {
            val old = queue.submit { throw CancellationException("old editor") }
            val next = queue.submit { executed = true }
            withTimeout(3000) { next.join() }
            assertTrue(old.isCancelled)
            assertTrue(executed)
        } finally { queue.close() }
    }

    @Test fun externalStepKeepsOriginalOwnerAcrossWorkerSwitch() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val owner = InputCommandOwner(1, 2) {}
        try {
            withContext(owner.context()) {
                withTimeout(3000) {
                    queue.execute { assertSame(owner, InputCommandOwner.requireOwner()) }
                }
            }
            assertNull(InputCommandOwner.current.get())
        } finally { queue.close() }
    }

    @Test fun closingQueueCancelsPendingWorkWithoutCancellingHost() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val first = queue.submit { entered.complete(Unit); awaitCancellation() }
        withTimeout(3000) { entered.await() }
        var ran = false
        val pending = queue.submit { ran = true }
        queue.close()
        withTimeout(3000) { joinAll(first, pending) }
        assertFalse(ran)
        assertTrue(coroutineContext.isActive)
        assertTrue(queue.submit { fail("closed") }.isCancelled)
    }
}
