package com.kingzcheung.xime.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrMemoryPressureTest {
    private val mb = 1L shl 20
    private val gb = 1L shl 30

    @Test fun `plenty of free memory keeps resident voice models`() {
        assertFalse(AsrMemoryPressure.isTight(availBytes = 4 * gb, thresholdBytes = 300 * mb, totalBytes = 12 * gb, lowMemory = false))
        assertFalse(AsrMemoryPressure.isTight(availBytes = 2 * gb, thresholdBytes = 300 * mb, totalBytes = 8 * gb, lowMemory = false))
    }

    @Test fun `system low memory flag releases`() {
        assertTrue(AsrMemoryPressure.isTight(availBytes = 4 * gb, thresholdBytes = 300 * mb, totalBytes = 12 * gb, lowMemory = true))
    }

    @Test fun `below twice the system low memory line releases`() {
        assertTrue(AsrMemoryPressure.isTight(availBytes = 500 * mb, thresholdBytes = 300 * mb, totalBytes = 3 * gb, lowMemory = false))
    }

    @Test fun `below one eighth of total memory releases`() {
        // 12 GB 机器剩不到 1.5 GB：系统已经在杀后台应用。
        assertTrue(AsrMemoryPressure.isTight(availBytes = 1400 * mb, thresholdBytes = 300 * mb, totalBytes = 12 * gb, lowMemory = false))
        assertFalse(AsrMemoryPressure.isTight(availBytes = 1600 * mb, thresholdBytes = 300 * mb, totalBytes = 12 * gb, lowMemory = false))
    }

    @Test fun `missing numbers never release by themselves`() {
        assertFalse(AsrMemoryPressure.isTight(availBytes = 1 * gb, thresholdBytes = 0, totalBytes = 0, lowMemory = false))
    }
}
