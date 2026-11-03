package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDropLogTest {
    @Test fun firstDropIsLoggedThenOneLinePerWindowWithTheCount() {
        var clock = 1_000L
        val lines = mutableListOf<String>()
        val log = InputDropLog({ clock }, { lines += it }, windowMs = 2_000L)
        var stateReads = 0
        val state = { stateReads++; "ready=false" }

        log.drop("key_not_ready", state)
        repeat(5) { clock += 100; log.drop("key_not_ready", state) }
        assertEquals(1, lines.size)
        assertEquals("Suppressed drops must not build the state text", 1, stateReads)

        clock += 2_000
        log.drop("touch_not_ready", state)
        assertEquals(2, lines.size)
        assertEquals("input_dropped reason=key_not_ready ready=false", lines[0])
        assertEquals("input_dropped reason=touch_not_ready more=5 ready=false", lines[1])
    }

    @Test fun aDropAtClockZeroIsStillLogged() {
        val lines = mutableListOf<String>()
        val log = InputDropLog({ 0L }, { lines += it })
        log.drop("key_not_ready") { "" }
        assertTrue(lines.single().startsWith("input_dropped reason=key_not_ready"))
    }
}
