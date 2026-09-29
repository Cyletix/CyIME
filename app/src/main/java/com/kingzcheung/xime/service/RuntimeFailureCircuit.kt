package com.kingzcheung.xime.service

/** Failure state belongs to one runtime instance, not a plugin name or IME lifetime. */
internal class RuntimeFailureCircuit(private val limit: Int) {
    private var runtime: Any? = null
    private var failures = 0
    @Synchronized fun permits(instance: Any): Boolean {
        if (runtime !== instance) { runtime = instance; failures = 0 }
        return failures < limit
    }
    @Synchronized fun record(instance: Any, failed: Boolean): Boolean {
        if (runtime !== instance) return false // A late result cannot poison a replacement.
        failures = if (failed) failures + 1 else 0
        return failures >= limit
    }
}
