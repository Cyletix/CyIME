package com.kingzcheung.xime.runtime.adaptation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface RuntimeSignalSource {
    fun capabilities(): DeviceCapabilities
    fun sample(): RuntimeSignals
    fun subscribe(onChanged: () -> Unit): AutoCloseable
}

/**
 * Visibility-owned observation loop. The caller serializes start/stop (IME main thread).
 * Sampling and diagnostics run on the supplied worker scope, never in the key path.
 * No model, microphone, preference writer or execution adapter is reachable from this owner.
 */
class RuntimeObserver(
    private val scope: CoroutineScope,
    private val source: RuntimeSignalSource,
    private val requests: () -> RuntimeRequests,
    private val onProposalChanged: (RuntimeObservation) -> Unit = {},
) {
    private val mutableObservation = MutableStateFlow<RuntimeObservation?>(null)
    val observation = mutableObservation.asStateFlow()
    private var job: Job? = null
    private var lastHistory: RuntimePolicyResolver.History? = null
    @Volatile private var generation = 0L

    fun start() {
        if (job?.isActive == true) return
        val token = ++generation
        job = scope.launch {
            val events = Channel<Unit>(Channel.CONFLATED)
            val subscription = source.subscribe { events.trySend(Unit) }
            try {
                val capabilities = source.capabilities()
                // Keep restrictions across hide/reopen, but never count the hidden interval as
                // healthy runtime or a second pressure observation.
                var history = synchronized(this@RuntimeObserver) {
                    lastHistory?.copy(pendingPressureSinceMs = null, healthySinceMs = null)
                }
                while (currentCoroutineContext().isActive) {
                    val signals = source.sample()
                    val next = RuntimePolicyResolver.resolve(capabilities, signals, requests(), history)
                    currentCoroutineContext().ensureActive()
                    val snapshot = RuntimeObservation(token, capabilities, signals, next.proposal)
                    val published = synchronized(this@RuntimeObserver) {
                        if (generation != token) false else {
                            mutableObservation.value = snapshot
                            lastHistory = next
                            true
                        }
                    }
                    if (!published) break
                    if (history?.proposal != next.proposal) onProposalChanged(snapshot)
                    history = next
                    withTimeoutOrNull(RuntimePolicyResolver.OBSERVATION_WINDOW_MS) { events.receive() }
                }
            } finally {
                subscription.close()
                events.close()
            }
        }
    }

    fun stop() {
        synchronized(this) {
            generation++
            mutableObservation.value = null
        }
        job?.cancel()
        job = null
    }
}
