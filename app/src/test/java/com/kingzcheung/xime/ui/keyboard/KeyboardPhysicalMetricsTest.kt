package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardPhysicalMetricsTest {
    private val reference = KeyboardPhysicalMetrics.from(1.875f, 266.471f)!!

    @Test fun userRoundedTabletDefaultDefinesPitchNotCapOrWholeScreenWidth() {
        val pitch = reference.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
        assertEquals(98.4f, pitch, 0.0001f)
        assertEquals(1000f, 16f + 10f * pitch, 0.001f)
        assertEquals(CALIBRATED_QWERTY_PITCH_MM.toFloat(), reference.millimetersFromDp(98.4f), 0.0001f)
        assertEquals(15.442f, reference.millimetersFromDp(98.4f - 12f), 0.002f)
        assertEquals(17.67589f, reference.millimetersFromDp(98.9f), 0.0001f)
    }

    @Test fun logicalDensityChangesDpButNotTheCalibratedPhysicalPitch() {
        for (density in listOf(1f, 1.5f, 1.875f, 2.5f, 3f)) {
            val metrics = KeyboardPhysicalMetrics.from(density, 266.471f)!!
            val pitch = metrics.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
            assertEquals(98.4f * 1.875f, pitch * density, 0.001f)
            assertEquals(CALIBRATED_QWERTY_PITCH_MM.toFloat(), metrics.millimetersFromDp(pitch), 0.0001f)
        }
    }

    @Test fun missingOrUnrepresentablePhysicalMetricsDoNotInventADeviceSize() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(KeyboardPhysicalMetrics.from(1.875f, bad))
            assertNull(KeyboardPhysicalMetrics.from(bad, 266.471f))
        }
        assertNull(KeyboardPhysicalMetrics.from(1f, Float.MAX_VALUE))
        assertNull(KeyboardPhysicalMetrics.from(1f, Float.MIN_VALUE))
    }

    @Test fun calibrationOnlyRecognizesActualQwertyRows() {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { row -> row.map(Char::toString) }
        assertTrue(hasStandardQwertyRows(rows))
        assertFalse(hasStandardQwertyRows(rows.map { it.reversed() }))
        assertFalse(hasStandardQwertyRows(rows.map { row -> row.map(String::uppercase) }))
        assertFalse(hasStandardQwertyRows(listOf(listOf("qw", "er", "ty", "ui", "op"))))
        assertFalse(hasStandardQwertyRows(rows.dropLast(1)))
    }
}
