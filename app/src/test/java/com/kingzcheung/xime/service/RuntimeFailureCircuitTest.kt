package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class RuntimeFailureCircuitTest {
    @Test fun reloadRestoresExecutionAndOldFailuresCannotPoisonNewRuntime() {
        val circuit = RuntimeFailureCircuit(2)
        val old = Any()
        assertTrue(circuit.permits(old))
        assertFalse(circuit.record(old, true))
        assertTrue(circuit.record(old, true))
        assertFalse(circuit.permits(old))
        val replacement = Any()
        assertTrue(circuit.permits(replacement))
        assertFalse(circuit.record(old, true))
        assertTrue(circuit.permits(replacement))
        circuit.record(replacement, true)
        circuit.record(replacement, false)
        assertFalse(circuit.record(replacement, true))
    }
}
