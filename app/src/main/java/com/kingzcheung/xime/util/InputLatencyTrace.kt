package com.kingzcheung.xime.util

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import com.kingzcheung.xime.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in Perfetto trace. No input/candidate text; no work in normal Debug/Release typing.
 * Ends at Compose draw submission, not physical display presentation.
 */
internal object InputLatencyTrace {
    private val sequence = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, Long>()
    fun begin(): Int {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 29 || !Trace.isEnabled()) return 0
        // An off-screen keyboard may never draw. Keep diagnostics bounded and identify drops.
        if (pending.size >= 128) pending.keys.minOrNull()?.let { finish(it, "dropped") }
        val id = sequence.incrementAndGet()
        pending[id] = SystemClock.elapsedRealtimeNanos()
        Trace.beginAsyncSection("CyIME.input", id)
        return id
    }
    inline fun <T> phase(id: Int, name: String, block: () -> T): T {
        if (id == 0) return block()
        Trace.beginSection("CyIME.$id.$name")
        return try { block() } finally { Trace.endSection() }
    }
    fun finish(id: Int, outcome: String) {
        if (id == 0 || Build.VERSION.SDK_INT < 29) return
        val start = pending.remove(id) ?: return
        phase(id, outcome) { Trace.setCounter("CyIME.input_us", (SystemClock.elapsedRealtimeNanos() - start) / 1000) }
        Trace.endAsyncSection("CyIME.input", id)
    }
}
