package com.kingzcheung.xime.clipboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardPinWriterTest {
    @Test fun quickUnpinCannotFinishBeforeAnEarlierBlockedPinAndBeOverwritten() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        val started = mutableListOf<Boolean>()
        var stored = false
        val writer = ClipboardPinWriter(this, CompletableDeferred(Unit), persist = { _, pinned ->
            started += pinned
            if (pinned) firstWrite.await()
            stored = pinned
        })
        val pin = writer.set(7, true)
        val unpin = writer.set(7, false)
        runCurrent()
        assertEquals(listOf(true), started)
        assertFalse(unpin.isCompleted)
        firstWrite.complete(Unit)
        runCurrent()
        assertTrue(pin.isCompleted)
        assertTrue(unpin.isCompleted)
        assertEquals(listOf(true, false), started)
        assertFalse(stored)
    }

    @Test fun userChangesWaitForMigrationAndKeepInvocationOrderAfterItFinishes() = runTest {
        val migration = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<Long, Boolean>>()
        val writer = ClipboardPinWriter(this, migration, persist = { id, pinned -> writes += id to pinned })
        val first = writer.set(8, true)
        val second = writer.set(8, false)
        val third = writer.set(9, true)
        runCurrent()
        assertTrue(writes.isEmpty())
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        migration.complete(Unit)
        runCurrent()
        assertEquals(listOf(8L to true, 8L to false, 9L to true), writes)
        assertTrue(third.isCompleted)
    }

    @Test fun failedWriteReportsFailureWithoutKillingTheWriterAndCanBeRetried() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val failures = mutableListOf<Throwable>()
        var fail = true
        var stored = false
        val writer = ClipboardPinWriter(scope, CompletableDeferred(Unit), persist = { _, pinned ->
            if (fail) error("database unavailable")
            stored = pinned
        }, onFailure = { failures += it })
        try {
            val failed = writer.set(7, true)
            runCurrent()
            assertTrue(failed.isCancelled)
            assertEquals(1, failures.size)
            assertFalse(stored)
            fail = false
            val retried = writer.set(7, true)
            runCurrent()
            assertTrue(retried.isCompleted)
            assertFalse(retried.isCancelled)
            assertTrue(stored)
        } finally {
            scope.cancel()
        }
    }

    @Test fun migrationPreservesImagesAndRemovesLegacyMarkersOnlyAfterAllTextWrites() = runTest {
        val pins = setOf("text:7", "image:content://image", "text:9")
        val migrated = mutableListOf<Long>()
        var remaining = pins
        migrateClipboardTextPins(pins, { migrated += it }) {
            assertEquals(listOf(7L, 9L), migrated)
            remaining = remaining.filterNot { it.startsWith("text:") }.toSet()
        }
        assertEquals(setOf("image:content://image"), remaining)
    }

    @Test fun failedMigrationKeepsItsMarkersAndCanRetryWithoutUnpinningExistingRecords() = runTest {
        val pins = setOf("text:7", "text:9", "image:content://image")
        val databasePins = mutableSetOf(3L)
        var removed = false
        val failure = runCatching {
            migrateClipboardTextPins(pins, { id ->
                if (id == 9L) error("database unavailable")
                databasePins += id
            }) { removed = true }
        }
        assertTrue(failure.isFailure)
        assertFalse(removed)
        assertEquals(setOf(3L, 7L), databasePins)
        migrateClipboardTextPins(pins, { databasePins += it }) { removed = true }
        assertTrue(removed)
        assertEquals(setOf(3L, 7L, 9L), databasePins)
    }
}
