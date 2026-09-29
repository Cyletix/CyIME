package com.kingzcheung.xime.keyboard

import java.util.concurrent.atomic.AtomicBoolean

/** One outstanding repeat per hold. A tap is never coalesced with a hold or another tap. */
class RepeatInput {
    private val active = AtomicBoolean(true)
    private val pending = AtomicBoolean(false)
    val isActive: Boolean get() = active.get()
    fun acquire(): Boolean = active.get() && pending.compareAndSet(false, true)
    fun completed() { pending.set(false) }
    fun stop() { active.set(false) }
    fun dispatch(action: () -> Unit) {
        val previous = current.get()
        current.set(this)
        try { action() } finally { current.set(previous) }
    }
    companion object { val current = ThreadLocal<RepeatInput?>() }
}
