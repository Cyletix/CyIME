package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardCapacityTest {
    @Test fun capacityIsWhatThisDeviceSearchesWithinTheBudget() {
        // 150 MB/s scanned: 40 ms reads 6 MB of text per keystroke.
        assertEquals(6_000_000L, ClipboardCapacity.capacity(150_000.0, 100L shl 30))
        // A nearly full phone: 2 % of 100 MB free is the tighter bound.
        assertEquals(2_000_000L, ClipboardCapacity.capacity(150_000.0, 100_000_000L))
    }

    @Test fun aTimingOverTooLittleTextIsNotTrusted() {
        assertNull(ClipboardCapacity.scanRate(100_000, 1.0))
        assertNull(ClipboardCapacity.scanRate(10_000_000, 0.0))
        assertEquals(100_000.0, ClipboardCapacity.scanRate(10_000_000, 100.0)!!, 0.0)
    }

    @Test fun measuredAgainAfterAQuarterMoreText() {
        assertFalse(ClipboardCapacity.shouldRemeasure(100_000, 0))
        assertTrue(ClipboardCapacity.shouldRemeasure(600_000, 0))
        assertFalse(ClipboardCapacity.shouldRemeasure(1_200_000, 1_000_000))
        assertTrue(ClipboardCapacity.shouldRemeasure(1_300_000, 1_000_000))
    }

    @Test fun recordsCountTheirUtf8TextAndSearchKey() {
        assertEquals(6L + 2, ClipboardCapacity.recordBytes("地址", "dz"))
        assertTrue(ClipboardCapacity.accepts(90, 10, 100))
        assertFalse(ClipboardCapacity.accepts(95, 10, 100))
    }

    @Test fun onlyRowsTheDatabaseCannotReadBackAreRefused() {
        assertTrue(ClipboardCapacity.fitsRow("a".repeat(ClipboardCapacity.ROW_BYTES)))
        assertFalse(ClipboardCapacity.fitsRow("a".repeat(ClipboardCapacity.ROW_BYTES + 1)))
        // Three bytes per character in UTF-8: a long Chinese copy is measured in bytes.
        assertFalse(ClipboardCapacity.fitsRow("地".repeat(ClipboardCapacity.ROW_BYTES / 3 + 1)))
    }

    @Test fun fullWhenARecordWasRefusedOrNothingIsLeft() {
        assertFalse(ClipboardRoom(10, 100).full)
        assertTrue(ClipboardRoom(10, 100, refused = true).full)
        assertTrue(ClipboardRoom(100, 100).full)
    }
}
