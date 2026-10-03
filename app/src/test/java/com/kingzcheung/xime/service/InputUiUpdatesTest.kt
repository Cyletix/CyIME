package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class InputUiUpdatesTest {
    @Test fun expiredEditorUpdateDoesNotDisableFollowingKeys() = runBlocking {
        val updates = Channel<suspend () -> Unit>(Channel.CONFLATED)
        val gate = InputReadiness().apply { completeStartup() }
        val owner = InputCommandOwner(gate.ticket()!!, 1) {}
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val refreshed = CompletableDeferred<Unit>()
        val consumer = launch { consumeInputUiUpdates(updates) { throw it } }
        try {
            updates.send {
                entered.complete(Unit)
                release.await()
                owner.requireCurrent(gate, 2)
            }
            withTimeout(3000) { entered.await() }
            gate.newEditorSession()
            updates.send { refreshed.complete(Unit) }
            release.complete(Unit)
            withTimeout(3000) { refreshed.await() }
            assertTrue(consumer.isActive)
        } finally { consumer.cancelAndJoin(); updates.close() }
    }

    @Test fun failedRefreshIsReportedAndNextRefreshStillRuns() = runBlocking {
        val updates = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        val errors = mutableListOf<Exception>()
        var refreshed = false
        updates.send { throw IllegalStateException("bad refresh") }
        updates.send { refreshed = true }
        updates.close()
        consumeInputUiUpdates(updates, errors::add)
        assertEquals("bad refresh", errors.single().message)
        assertTrue(refreshed)
    }

    @Test fun serviceDestructionStillCancelsTheConsumer() = runBlocking {
        val updates = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        val entered = CompletableDeferred<Unit>()
        var refreshed = false
        updates.send { entered.complete(Unit); awaitCancellation() }
        updates.send { refreshed = true }
        val consumer = launch { consumeInputUiUpdates(updates) { throw it } }
        withTimeout(3000) { entered.await() }
        consumer.cancelAndJoin()
        updates.close()
        assertFalse(refreshed)
        assertTrue(consumer.isCancelled)
    }
}
