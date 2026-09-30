package com.kingzcheung.xime.model

import org.junit.Assert.*
import org.junit.Test

class DeviceDefaultsTest {
    @Test fun lowMemoryAndLowRamDevicesNeverEnableExpensiveDefaults() {
        assertFalse(DeviceDefaults.supportsRefinement(true, 8L * 1024 * 1024 * 1024, 8))
        assertFalse(DeviceDefaults.supportsRefinement(false, 4L * 1024 * 1024 * 1024, 8))
        assertFalse(DeviceDefaults.supportsRefinement(false, 8L * 1024 * 1024 * 1024, 2))
    }
    @Test fun capableDevicesEnableRefinementAtTheDocumentedBoundary() {
        assertTrue(DeviceDefaults.supportsRefinement(false, 6L * 1024 * 1024 * 1024, 4))
        assertFalse(DeviceDefaults.supportsRefinement(false, 6L * 1024 * 1024 * 1024 - 1, 4))
    }
}
