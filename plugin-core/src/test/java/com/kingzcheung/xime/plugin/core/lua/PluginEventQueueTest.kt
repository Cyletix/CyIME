package com.kingzcheung.xime.plugin.core.lua

import org.junit.Assert.*
import org.junit.Test

class PluginEventQueueTest {
    private fun input(text: String) = PluginEvent(PluginEvent.TYPE_INPUT_CHANGED, mapOf("input_text" to text))
    private fun commit(text: String, paste: Boolean) = PluginEvent(PluginEvent.TYPE_TEXT_COMMITTED, mapOf("committed_text" to text, "is_paste" to paste))
    @Test fun inputSnapshotsNeverOverwriteTypedOrPastedCommits() {
        val queue = PluginEventQueue()
        val typed = commit("中", false)
        val pasted = commit("paste", true)
        listOf(input("n"), input("ni"), typed, input(""), pasted, input("a"), input("ab")).forEach { assertTrue(queue.offer(it)) }
        assertEquals(listOf(input("ni"), typed, input(""), pasted, input("ab")), generateSequence { queue.poll() }.toList())
    }
    @Test fun fullQueueRejectsEffectExplicitlyWithoutEvictingAcceptedCommits() {
        val queue = PluginEventQueue(1)
        val first = commit("a", false)
        assertTrue(queue.offer(first))
        assertFalse(queue.offer(commit("b", true)))
        assertEquals(first, queue.poll())
    }
}
