package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class EditorReadCapabilityTest {
    @Test fun restartsAndChangedConnectionsDoNotReusePreviousCapabilities() {
        val capability = EditorReadCapability()
        val first = Any()
        var probes = 0
        assertTrue(capability.supports(1, first) { probes++; true })
        assertTrue(capability.supports(1, first) { error("Repeated query") })
        assertFalse(capability.supports(2, first) { probes++; false })
        assertFalse(capability.supports(1, Any()) { probes++; false })
        assertEquals(3, probes)
    }

    @Test fun temporarilyUnreadableEditorCanRecover() {
        val capability = EditorReadCapability()
        val connection = Any()
        assertFalse(capability.supports(1, connection) { false })
        assertTrue(capability.supports(1, connection) { true })
        assertTrue(capability.supports(1, connection) { error("Repeated query") })
    }

    @Test fun explicitEndOfInputDropsCapability() {
        val capability = EditorReadCapability()
        val connection = Any()
        assertTrue(capability.supports(1, connection) { true })
        capability.clear()
        assertFalse(capability.supports(1, connection) { false })
    }
}
