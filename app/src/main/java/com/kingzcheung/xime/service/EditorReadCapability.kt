package com.kingzcheung.xime.service

/** Cache capability, never editor text. Actual candidate replacement still verifies the text. */
internal class EditorReadCapability {
    private data class ReadableEditor(val session: Long, val connection: Any)
    @Volatile private var readable: ReadableEditor? = null

    fun supports(session: Long, connection: Any, probe: () -> Boolean): Boolean {
        val known = readable
        if (known?.session == session && known.connection === connection) return true
        // A temporarily unavailable editor must be allowed to recover without refocusing.
        val supported = probe()
        if (supported) readable = ReadableEditor(session, connection)
        return supported
    }

    fun clear() { readable = null }
}
