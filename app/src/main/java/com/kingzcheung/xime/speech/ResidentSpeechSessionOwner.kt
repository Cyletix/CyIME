package com.kingzcheung.xime.speech

/** One use of the resident engine: dictation ([LocalSpeechSession]) or live captions. */
internal interface ResidentSession {
    fun acceptPcm(bytes: ByteArray)
    fun finish(): String
    fun cancel()
    fun awaitIdle()
    val isIdle: Boolean
}

/** Keeps model weights resident while serializing all uses of their native recognizers. */
internal class ResidentSpeechSessionOwner(
    private val retire: (() -> Unit) -> Unit = { task ->
        Thread(task, "CyIME-AsrRelease").apply { isDaemon = true }.start()
    },
) {
    private class OwnedSession(val session: ResidentSession, val owner: Any?, var finishing: Boolean = false)

    private val stateLock = Any()
    private val engineLock = Any()
    private var request = 0L
    private var starting: Long? = null
    private var startingOwner: Any? = null
    private var current: OwnedSession? = null
    private var last: OwnedSession? = null
    // Model ownership changes only under engineLock, after the last session is truly idle.
    private var engine: LocalSpeechEngine? = null
    private var loadedKey: String? = null

    val isIdle: Boolean get() = synchronized(stateLock) { current == null && starting == null }

    fun start(
        key: String,
        createEngine: () -> LocalSpeechEngine,
        createSession: (LocalSpeechEngine) -> ResidentSession,
        owner: Any? = null,
        isActive: () -> Boolean = { true },
    ): Boolean {
        val (ticket, previous) = synchronized(stateLock) {
            if (!isActive()) return false
            val ticket = ++request
            starting = ticket
            startingOwner = owner
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
                val next = OwnedSession(createSession(checkNotNull(engine)), owner)
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
            synchronized(stateLock) { if (starting == ticket) { starting = null; startingOwner = null } }
        }
    }

    fun acceptPcm(audio: ByteArray, owner: Any? = null) {
        val owned = synchronized(stateLock) { current?.takeIf { it.owner == owner && !it.finishing } }
        checkNotNull(owned) { "语音会话已结束或已被新的录音替代" }.session.acceptPcm(audio)
    }

    /** Finishing never holds the state/model lock; a newer request retains its own session. */
    fun finish(owner: Any? = null, afterFinish: () -> Unit = {}): String? {
        val owned = synchronized(stateLock) {
            current?.takeIf { it.owner == owner && !it.finishing }?.also { it.finishing = true }
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

    fun cancel(owner: Any? = null): Boolean {
        val owned = synchronized(stateLock) {
            if (current?.owner != owner && !(starting != null && startingOwner == owner)) return false
            request++
            starting = null
            startingOwner = null
            current.also { current = null }
        }
        owned?.session?.cancel()
        return true
    }

    /** Explicit/idle release waits in the background; an older release cannot close a new session. */
    fun release(onlyIfIdle: Boolean = false, owner: Any? = null): Boolean {
        val (ticket, owned) = synchronized(stateLock) {
            if (onlyIfIdle && (current != null || starting != null)) return false
            // null is reserved for the service's own shutdown/idle cleanup.
            if (owner != null && (current?.owner ?: startingOwner ?: last?.owner) != owner) return false
            val ticket = ++request
            starting = null
            startingOwner = null
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

    /**
     * 语音内存阶梯第一级：空闲时只卸掉第二遍识别器，流式模型继续常驻。
     * 在后台线程里等模型锁，不阻塞调用方；期间有新会话开始就放弃。
     */
    fun releaseRefinementIfIdle(onReleased: () -> Unit = {}) {
        if (!isIdle) return
        retire {
            synchronized(engineLock) {
                if (!isIdle) return@synchronized
                synchronized(stateLock) { last }?.session?.awaitIdle()
                if (isIdle && engine?.releaseRefinement() == true) onReleased()
            }
        }
    }

    private fun isLatest(ticket: Long): Boolean = synchronized(stateLock) { request == ticket }

    private fun closeEngine() {
        try { engine?.close() }
        finally { engine = null; loadedKey = null }
    }
}
