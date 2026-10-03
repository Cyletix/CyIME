package com.kingzcheung.xime.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Preserve user action order even when an earlier Room write or migration is still running. */
internal class ClipboardPinWriter(
    private val scope: CoroutineScope,
    private val ready: Deferred<Unit>,
    private val persist: suspend (Long, Boolean) -> Unit,
    onFailure: (Throwable) -> Unit = {},
) {
    private val mutex = Mutex()
    private val exceptionHandler = CoroutineExceptionHandler { _, error -> onFailure(error) }

    fun set(id: Long, pinned: Boolean): Job = scope.launch(exceptionHandler, start = CoroutineStart.UNDISPATCHED) {
        mutex.withLock {
            ready.await()
            persist(id, pinned)
        }
    }
}

/** Consume legacy markers only after every database pin has been written successfully. */
internal suspend fun migrateClipboardTextPins(storedPins: Set<String>, persist: suspend (Long) -> Unit,
    removeLegacyMarkers: () -> Unit) {
    if (storedPins.none { it.startsWith("text:") }) return
    legacyClipboardTextPinIds(storedPins).forEach { persist(it) }
    removeLegacyMarkers()
}
