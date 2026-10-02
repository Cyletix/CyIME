package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareToolbarGeometryTest {
    @Test fun defaultIsBottomCenteredWithinRealHost() {
        val geometry = hardwareToolbarGeometry(900, 600, 232, 56, 8)
        assertEquals(HardwareToolbarOffset(334f, 536f), geometry.offset(HardwareToolbarPosition()))
    }

    @Test fun releaseSnapsToThreeHorizontalDocksAndKeepsVerticalTravel() {
        val geometry = hardwareToolbarGeometry(900, 600, 232, 56, 8)
        for ((fraction, dock) in listOf(0.05f to 0f, 0.24f to 0f, 0.3f to 0.5f, 0.7f to 0.5f, 0.76f to 1f, 0.98f to 1f)) {
            val offset = geometry.offset(HardwareToolbarPosition(fraction, 0.35f))
            val result = geometry.positionAt(offset.x, offset.y, HardwareToolbarPosition())
            assertEquals(dock, result.xFraction, 0.0001f)
            assertEquals(0.35f, result.yFraction, 0.0001f)
        }
    }

    @Test fun movingBeforeReleaseDoesNotSnapOrLeaveHost() {
        val geometry = hardwareToolbarGeometry(900, 600, 232, 56, 8)
        val offset = geometry.offset(HardwareToolbarPosition(0.37f, 0.62f))
        val result = geometry.positionAt(offset.x, offset.y, HardwareToolbarPosition(), snapX = false)
        assertEquals(0.37f, result.xFraction, 0.0001f)
        assertEquals(0.62f, result.yFraction, 0.0001f)
        assertEquals(HardwareToolbarOffset(8f, 536f), geometry.constrain(-300f, 3000f))
    }

    @Test fun savedPositionAdaptsToRotationSplitAndToolbarExpansion() {
        val position = HardwareToolbarPosition(1f, 0.4f)
        for ((width, height) in listOf(900 to 600, 600 to 900, 260 to 480)) {
            for (toolbarHeight in listOf(56, 104)) {
                val geometry = hardwareToolbarGeometry(width, height, 232, toolbarHeight, 8)
                val offset = geometry.offset(position)
                assertEquals((width - 232 - 8).toFloat(), offset.x, 0.0001f)
                assertTrue(offset.y >= 8f && offset.y + toolbarHeight <= height - 8f)
                val restored = geometry.positionAt(offset.x, offset.y, position)
                assertEquals(position.xFraction, restored.xFraction, 0.0001f)
                assertEquals(position.yFraction, restored.yFraction, 0.0001f)
            }
        }
    }

    @Test fun noTravelPreservesExistingDockAndTinyHostsStayValid() {
        val geometry = hardwareToolbarGeometry(48, 48, 48, 48, 8)
        val position = HardwareToolbarPosition(1f, 0.7f)
        assertEquals(HardwareToolbarOffset(0f, 0f), geometry.offset(position))
        assertEquals(position, geometry.positionAt(50f, 50f, position))
        assertEquals(HardwareToolbarOffset(0f, 0f), hardwareToolbarGeometry(0, 0, 232, 56, 8).offset(position))
        assertEquals(HardwareToolbarOffset(1f, 2f), hardwareToolbarGeometry(50, 52, 48, 48, 8).offset(position))
    }

    @Test fun restoredInvalidFractionsReturnToSafeDefaults() {
        val geometry = hardwareToolbarGeometry(900, 600, 232, 56, 8)
        assertEquals(geometry.offset(HardwareToolbarPosition()), geometry.offset(HardwareToolbarPosition(Float.NaN, Float.POSITIVE_INFINITY)))
        assertEquals(HardwareToolbarOffset(8f, 536f), geometry.offset(HardwareToolbarPosition(-3f, 7f)))
    }

    @Test fun narrowLayoutsAlwaysRetainKeyboardRecovery() {
        assertEquals(HardwareToolbarMode.FULL, hardwareToolbarMode(232f))
        assertEquals(HardwareToolbarMode.COMPACT, hardwareToolbarMode(231f))
        assertEquals(HardwareToolbarMode.COMPACT, hardwareToolbarMode(184f))
        assertEquals(HardwareToolbarMode.KEYBOARD_ONLY, hardwareToolbarMode(183f))
        assertEquals(HardwareToolbarMode.KEYBOARD_ONLY, hardwareToolbarMode(48f))
        assertEquals(HardwareToolbarMode.KEYBOARD_ONLY, hardwareToolbarMode(0f))
    }
}
