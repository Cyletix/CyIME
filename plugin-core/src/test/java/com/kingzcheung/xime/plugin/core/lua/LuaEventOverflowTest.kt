package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LuaEventOverflowTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun runtimeDrainsAcceptedCommitsThenReportsGapAndFailsStream() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gap = CountDownLatch(1)
        val values = ConcurrentHashMap<String, String>()
        val store = object : PluginConfigStore {
            override fun get(key: String) = values[key]
            override fun keys() = values.keys.toSet()
            override fun remove(key: String) { values.remove(key) }
            override fun set(key: String, value: String) {
                if (key == "hold") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                values[key] = value
                if (key == "gap") gap.countDown()
            }
        }
        val dir = temp.newFolder()
        File(dir, "main.lua").writeText("""
            local seen = ''
            return { onPluginEvent = function(kind, p)
              if kind == 'text_committed' then
                if p.sequence == 1 then host.config.set('hold', '1') end
                seen = seen .. p.committed_text
                host.config.set('seen', seen)
              elseif kind == 'event_stream_failed' then
                host.config.set('gap', tostring(p.first_missing_sequence))
              end
            end }
        """.trimIndent())
        val runtime = LuaScriptRuntime("overflow-test", dir, "main.lua", store, eventQueueCapacity = 2)
        runtime.initEvents(setOf(PluginEvent.TYPE_TEXT_COMMITTED))
        fun event(text: String) = PluginEvent(PluginEvent.TYPE_TEXT_COMMITTED, mapOf("committed_text" to text))
        try {
            assertTrue(runtime.load())
            assertTrue(runtime.dispatchEvent(event("a")))
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertTrue(runtime.dispatchEvent(event("b")))
            assertTrue(runtime.dispatchEvent(event("c")))
            assertFalse(runtime.dispatchEvent(event("d")))
            assertNotNull(runtime.eventStreamFailure)
            assertFalse(runtime.dispatchEvent(event("e")))
            release.countDown()
            assertTrue(gap.await(5, TimeUnit.SECONDS))
            assertEquals("abc", values["seen"])
            assertEquals("4", values["gap"])
        } finally { release.countDown(); runtime.close() }
    }
}
