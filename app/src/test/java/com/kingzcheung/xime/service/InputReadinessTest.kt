package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class InputReadinessTest {
    @Test fun delayedSystemTouchCannotBecomeAKeyAfterStartup() {
        var clock = 10L
        val gate = InputReadiness { clock }
        clock = 100L
        gate.completeStartup()
        assertFalse(gate.acceptsEvent(20L))
        assertTrue(gate.acceptsEvent(101L))
        gate.deployment(false) // Repeated completion must not cancel a valid ongoing touch.
        assertTrue(gate.acceptsEvent(101L))
    }
    @Test fun nativeInitializationCompletionDoesNotEnableTyping() {
        val gate = InputReadiness()
        gate.deployment(true)
        gate.deployment(false)
        assertNull(gate.ticket())
        gate.completeStartup()
        assertNotNull(gate.ticket())
    }
    @Test fun keysTappedDuringDeploymentNeverAcquireTickets() {
        val gate = InputReadiness()
        val taps = List(100) { gate.ticket() }
        gate.completeStartup()
        assertTrue(taps.all { it == null })
        assertTrue(gate.accepts(gate.ticket()!!))
    }
    @Test fun redeploymentDiscardsOldQueueEntriesEvenAfterItFinishes() {
        val gate = InputReadiness()
        gate.completeStartup()
        val old = gate.ticket()!!
        gate.deployment(true)
        assertNull(gate.ticket())
        assertFalse(gate.accepts(old))
        gate.deployment(false)
        assertFalse(gate.accepts(old))
        assertTrue(gate.accepts(gate.ticket()!!))
    }
}
