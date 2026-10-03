package com.kingzcheung.xime.speech

/** Keeps model weights resident while serializing all uses of their native recognizers. */
internal class ResidentSpeechSessionOwner(
    private val retire: (() -> Unit) -> Unit = { task ->
        Thread(task, "CyIME-AsrRelease").apply { isDaemon = true }.start()
    },
) {
    private class OwnedSession(val session: LocalSpeechSession, var finishing: Boolean = false)

    private val stateLock = Any()
    private val engineLock = Any()
    private var request = 0L
    private var starting: Long? = null
    private var current: OwnedSession? = null
    private var last: OwnedSession? = null
    // Model ownership changes only under engineLock, after the last session is truly idle.
    private var engine: LocalSpeechEngine? = null
    private var loadedKey: String? = null

    val isIdle: Boolean get() = synchronized(stateLock) { current == null && starting == null }

    fun start(
        key: String,
        createEngine: () -> LocalSpeechEngine,
        createSession: (LocalSpeechEngine) -> LocalSpeechSession,
        isActive: () -> Boolean = { true },
    ): Boolean {
        val (ticket, previous) = synchronized(stateLock) {
            if (!isActive()) return false
            val ticket = ++request
            starting = ticket
            val previous = current?.takeUnless { it.finishing }
            current = null
            ticket to previous
        }
        previous?.session?.cancel()
        try {
            return synchronized(engineLock) {
                if (!isLatest(ticket) || !isActive()) return@synchronized false
                val predecessor = synchronized(stateLock) { last }
                predecessor?.session?.awaitIdle()
                if (!isLatest(ticket) || !isActive()) return@synchronized false
                if (loadedKey != key) {
                    closeEngine()
                    engine = createEngine()
                    loadedKey = key
                }
                if (!isLatest(ticket) || !isActive()) return@synchronized false
                val next = OwnedSession(createSession(checkNotNull(engine)))
                synchronized(stateLock) {
                    if (request != ticket || !isActive()) {
                        next.session.cancel()
                        false
                    } else {
                        current = next
                        last = next
                        true
                    }
                }
            }
        } finally {
            synchronized(stateLock) { if (starting == ticket) starting = null }
        }
    }

    fun acceptPcm(audio: ByteArray) {
        synchronized(stateLock) { current }?.session?.acceptPcm(audio)
    }

    /** Finishing never holds the state/model lock; a newer request retains its own session. */
    fun finish(afterFinish: () -> Unit = {}): String? {
        val owned = synchronized(stateLock) {
            current?.takeUnless { it.finishing }?.also { it.finishing = true }
        } ?: return null
        try {
            return owned.session.finish()
        } finally {
            // Also terminates both queues if finish was interrupted or threw.
            owned.session.cancel()
            synchronized(stateLock) { if (current === owned) current = null }
            afterFinish()
        }
    }

    fun cancel() {
        val owned = synchronized(stateLock) {
            request++
            starting = null
            current.also { current = null }
        }
        owned?.session?.cancel()
    }

    /** Explicit/idle release waits in the background; an older release cannot close a new session. */
    fun release(onlyIfIdle: Boolean = false): Boolean {
        val (ticket, owned) = synchronized(stateLock) {
            if (onlyIfIdle && (current != null || starting != null)) return false
            val ticket = ++request
            starting = null
            ticket to current.also { current = null }
        }
        owned?.session?.cancel()
        retire {
            synchronized(engineLock) {
                if (isLatest(ticket)) {
                    synchronized(stateLock) { last }?.session?.awaitIdle()
                    if (isLatest(ticket)) {
                        closeEngine()
                        synchronized(stateLock) { last = null }
                    }
                }
            }
        }
        return true
    }

    private fun isLatest(ticket: Long): Boolean = synchronized(stateLock) { request == ticket }

    private fun closeEngine() {
        try { engine?.close() }
        finally { engine = null; loadedKey = null }
    }
}
