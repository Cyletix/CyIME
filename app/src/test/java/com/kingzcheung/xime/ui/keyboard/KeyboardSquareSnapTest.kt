package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.LayoutKind
import org.junit.Assert.*
import org.junit.Test

class KeyboardSquareSnapTest {
    @Test fun squareCapsSnapAndReleaseForEverySupportedLayoutAndEdge() {
        val geometries = listOf(KeyboardSquareSnap.forLayout(LayoutKind.ALPHABETIC, false)!!,
            KeyboardSquareSnap.forLayout(LayoutKind.ALPHABETIC, true)!!,
            KeyboardSquareSnap.forLayout(LayoutKind.T9, false)!!)
        for (g in geometries) for (floating in listOf(false, true)) for (density in listOf(1f, 2f)) {
            val extra = if (floating) 24f * density else 16f * density
            val bounds = ResizeRect(0f, 0f, 2000f * density, 1600f * density)
            val spacing = 6f to 10f
            val seed = ResizeRect(300f * density, 300f * density, 1000f * density, 600f * density)
            var low = 60f * density + extra
            var high = 1000f * density + extra
            repeat(24) {
                val mid = (low + high) / 2f
                if (g.capDifference(seed.copy(bottom = seed.top + mid), density, extra, floating, spacing) > 0f)
                    low = mid else high = mid
            }
            val square = seed.copy(bottom = seed.top + (low + high) / 2f)
            for (handle in listOf(ResizeHandle.LEFT, ResizeHandle.RIGHT, ResizeHandle.TOP, ResizeHandle.BOTTOM,
                ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_RIGHT)) {
                fun offset(d: Float) = when (handle) {
                    ResizeHandle.LEFT, ResizeHandle.TOP_LEFT -> square.copy(left = square.left + d)
                    ResizeHandle.RIGHT, ResizeHandle.BOTTOM_RIGHT -> square.copy(right = square.right + d)
                    ResizeHandle.TOP -> square.copy(top = square.top + d)
                    else -> square.copy(bottom = square.bottom + d)
                }
                val near = offset(3f * density)
                val snapped = near.snapSquareKeys(handle, g, bounds, density, extra, floating, spacing)
                assertEquals(0f, g.capDifference(snapped, density, extra, floating, spacing), 0.001f)
                val far = offset(12f * density)
                assertEquals(far, far.snapSquareKeys(handle, g, bounds, density, extra, floating, spacing))
            }
        }
    }
    @Test fun viewportEdgeWinsOverNearbySquarePoint() {
        val g = KeyboardSquareSnap(10f, false, false)
        val r = ResizeRect(0f, 100f, 716f, 432f)
        val bounds = ResizeRect(0f, 0f, 716f, 1000f)
        assertEquals(r, r.snapSquareKeys(ResizeHandle.RIGHT, g, bounds, 1f, 0f, false, 0f to 0f))
    }
    @Test fun movingWholeKeyboardDoesNotSnap() {
        val r = ResizeRect(100f, 100f, 800f, 500f)
        assertEquals(r, r.snapSquareKeys(ResizeHandle.NONE, KeyboardSquareSnap(10f, false, false),
            ResizeRect(0f, 0f, 1600f, 1200f), 1f, 0f, false, null to null))
    }
}
