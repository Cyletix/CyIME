package com.kingzcheung.xime.keyboard

import java.util.concurrent.atomic.AtomicBoolean

/** One outstanding repeat per hold. A tap is never coalesced with a hold or another tap. */
class RepeatInput {
    private val active = AtomicBoolean(true)
    private val pending = AtomicBoolean(false)
    private var deletedImeContent = false
    val isActive: Boolean get() = active.get()
    fun acquire(): Boolean = active.get() && pending.compareAndSet(false, true)
    fun completed() { pending.set(false) }
    fun stop() { active.set(false) }
    /** Called in delete FIFO order. Once this press consumes composition, only a new press may
     * start deleting text that was already in the editor. Dismissing candidates does not count. */
    fun allowsDeletion(imeContent: Boolean): Boolean {
        if (imeContent) deletedImeContent = true else if (deletedImeContent) stop()
        return isActive
    }
    fun dispatch(action: () -> Unit) {
        val previous = current.get()
        current.set(this)
        try { action() } finally { current.set(previous) }
    }
    companion object { val current = ThreadLocal<RepeatInput?>() }
}
