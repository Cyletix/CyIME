package com.kingzcheung.xime.plugin.core.lua

/** Only adjacent state snapshots coalesce. Committed text is an ordered effect. */
internal class PluginEventQueue(private val capacity: Int = 256) {
    private val events = java.util.ArrayDeque<PluginEvent>()
    @Synchronized fun offer(event: PluginEvent): Boolean {
        if (event.type == PluginEvent.TYPE_TYPING_TOTALS) {
            events.removeAll { it.type == PluginEvent.TYPE_TYPING_TOTALS }
        }
        val state = event.type == PluginEvent.TYPE_INPUT_CHANGED || event.type == PluginEvent.TYPE_QUICK_SEND_CHANGED
        // A queue containing only state has no intervening effects whose order must be kept.
        if (state && events.none { it.type == PluginEvent.TYPE_TEXT_COMMITTED }) {
            events.removeAll { it.type == event.type }
        }
        if (state && events.peekLast()?.type == event.type) events.removeLast()
        if (events.size >= capacity) return false
        events.addLast(event)
        return true
    }
    @Synchronized fun poll(): PluginEvent? = events.pollFirst()
    @Synchronized fun clear() = events.clear()
}
