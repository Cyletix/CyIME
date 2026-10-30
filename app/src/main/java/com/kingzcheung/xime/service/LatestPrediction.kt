package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One inference at a time, with only the latest pending context retained. */
internal class LatestPrediction(
    private val scope: CoroutineScope,
    private val predict: suspend (String) -> List<String>,
    private val deliver: (List<String>) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val gate = Mutex()
    private var job: Job? = null
    @Volatile private var generation = 0L
    private val pendingGeneration = MutableStateFlow(0L)
    /** Zero means no pending request; the generation keeps stale completions from clearing a newer request. */
    val pendingRequest: StateFlow<Long> = pendingGeneration.asStateFlow()
    val isPending: Boolean get() = pendingGeneration.value != 0L
    val revision: Long get() = generation
    private var nextAllowed = 0L
    // 上屏后到请求真正 submit 之间（中间隔一次 Main post）的登记；不显示在 pendingRequest 里。
    private var reservation: Long? = null

    // 按下退格时作废掉的请求：下一次 invalidate（松手那一下退格）仍报“在等”，中间有别的作废就失效。
    private var heldCancel: Long? = null

    @Synchronized fun invalidate(): Boolean {
        val held = heldCancel == generation
        heldCancel = null
        generation++
        val reserved = reservation != null
        reservation = null
        val pending = pendingGeneration.getAndUpdate { 0L } != 0L
        job?.cancel(); job = null
        return pending || reserved || held
    }

    /** Invalidates now; when something was pending, the next [invalidate] still reports it. */
    @Synchronized fun invalidateAndHold(): Boolean {
        val pending = invalidate()
        if (pending) heldCancel = generation
        return pending
    }

    /** Marks a request that will be submitted after a post; [invalidate] reports it as pending. */
    @Synchronized fun reserve(): Long {
        reservation = generation
        return generation
    }

    /** Drops a reservation that ended without a submit; a newer request is left alone. */
    @Synchronized fun release(token: Long) {
        if (reservation == token && generation == token) reservation = null
    }

    @Synchronized fun submit(text: String) {
        invalidate()
        val request = generation
        pendingGeneration.value = request
        job = scope.launch {
            try {
                delay(160) // Let a burst settle before starting expensive native inference.
                gate.withLock {
                    delay((nextAllowed - now()).coerceAtLeast(0))
                    ensureActive()
                    val start = now()
                    try {
                        val result = predict(text)
                        ensureActive()
                        if (request == generation) deliver(result)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    finally {
                        // A non-cancellable Binder call retains the gate until it returns.
                        // Slow devices get a cooldown rather than a growing native queue.
                        val elapsed = now() - start
                        nextAllowed = now() + if (elapsed > 350) elapsed.coerceAtMost(1500) else 0
                    }
                }
            } finally { pendingGeneration.compareAndSet(request, 0L) }
        }
    }
}
