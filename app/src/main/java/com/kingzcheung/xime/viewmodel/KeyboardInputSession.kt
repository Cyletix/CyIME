package com.kingzcheung.xime.viewmodel

/** Editor identity outlives the visible keyboard (for example, its hardware toolbar mode). */
internal class KeyboardInputSession {
    private var sessionId: Long? = null
    private var resetSignal: Long? = null

    val initialized: Boolean get() = sessionId != null

    fun update(sessionId: Long, resetSignal: Long): Change {
        val newSession = this.sessionId != sessionId
        val resetController = newSession || this.resetSignal != resetSignal
        this.sessionId = sessionId
        this.resetSignal = resetSignal
        return Change(newSession, resetController)
    }

    data class Change(val newSession: Boolean, val resetController: Boolean)
}
