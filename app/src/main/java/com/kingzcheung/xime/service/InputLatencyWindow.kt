package com.kingzcheung.xime.service

/** Bounded timings of queued actions; no key, editor identity or text is collected. */
internal class InputLatencyWindow {
    private val waits = LongArray(64)
    private val executions = LongArray(64)
    private var count = 0

    data class Summary(val samples: Int, val totalMedianMs: Long, val totalP95Ms: Long,
        val totalMaxMs: Long, val waitP95Ms: Long, val executionP95Ms: Long)

    @Synchronized fun record(waitMs: Long, executionMs: Long): Summary? {
        waits[count] = waitMs.coerceAtLeast(0)
        executions[count] = executionMs.coerceAtLeast(0)
        count++
        if (count < waits.size) return null
        val totals = LongArray(count) { waits[it] + executions[it] }.apply { sort() }
        waits.sort()
        executions.sort()
        val p95 = (count * 95 + 99) / 100 - 1
        val summary = Summary(count, totals[(count - 1) / 2], totals[p95], totals.last(),
            waits[p95], executions[p95])
        count = 0
        return summary
    }
}
