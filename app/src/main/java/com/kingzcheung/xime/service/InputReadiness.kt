package com.kingzcheung.xime.service

/** No key may enter a queue before startup has completed. Deployments invalidate queued input. */
internal class InputReadiness(private val uptimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private var ready = false
    private var generation = 0L
    private var readySince = Long.MAX_VALUE
    private var startupComplete = false

    @Synchronized fun deployment(active: Boolean) {
        if (active) { ready = false; generation++ }
        else if (startupComplete && !ready) { readySince = uptimeMillis(); ready = true }
    }
    @Synchronized fun completeStartup() { startupComplete = true; readySince = uptimeMillis(); ready = true }
    @Synchronized fun acceptsEvent(downTime: Long): Boolean = ready && downTime >= readySince
    @Synchronized fun ticket(): Long? = if (ready) generation else null
    @Synchronized fun accepts(ticket: Long): Boolean = ready && generation == ticket
}
