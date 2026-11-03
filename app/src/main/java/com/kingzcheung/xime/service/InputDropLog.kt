package com.kingzcheung.xime.service

/**
 * Input the keyboard refused without telling the user (no overlay, no toast): one log line, then at most one
 * line per [windowMs] carrying how many more were refused meanwhile. Never the key or any text, only the reason
 * and the gate state, so a report like 「进聊天页打不出字」 can be traced from the log afterwards.
 */
internal class InputDropLog(
    private val uptimeMillis: () -> Long,
    private val write: (String) -> Unit,
    private val windowMs: Long = 2_000L,
) {
    private var lastAt = 0L
    private var logged = false
    private var suppressed = 0

    @Synchronized fun drop(reason: String, state: () -> String) {
        val now = uptimeMillis()
        if (logged && now - lastAt < windowMs) { suppressed++; return }
        val extra = if (suppressed > 0) " more=$suppressed" else ""
        logged = true
        lastAt = now
        suppressed = 0
        write("input_dropped reason=$reason$extra ${state()}")
    }
}
