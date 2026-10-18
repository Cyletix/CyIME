package com.kingzcheung.xime.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrImageGeometryTest {
    @Test fun centeredUntransformedImageUsesTheSameStripAsCamera() {
        val crop = OcrScanGeometry.crop(1000, 500, 1000, 500)
        val output = OcrImageGeometry.output(1000, 500, 1000, 500, OcrImageTransform())
        assertEquals(crop.width, output.width)
        assertEquals(crop.height, output.height)
        assertEquals(-crop.left.toFloat(), output.left, .01f)
        assertEquals(-crop.top.toFloat(), output.top, .01f)
    }

    @Test fun draggingMovesOnlyImageAndKeepsOutputGuideDimensions() {
        val original = OcrImageGeometry.output(1000, 500, 1000, 500, OcrImageTransform())
        val moved = OcrImageGeometry.output(1000, 500, 1000, 500, OcrImageTransform(panX = 123f, panY = -45f))
        assertEquals(original.width, moved.width)
        assertEquals(original.height, moved.height)
        assertEquals(original.left + 123f, moved.left, .01f)
        assertEquals(original.top - 45f, moved.top, .01f)
    }

    @Test fun zoomKeepsTheImagePointUnderTheFingers() {
        val zoomed = OcrImageTransform().gesture(1000, 500, 250f, 100f, 0f, 0f, 2f)
        val projection = OcrImageGeometry.projection(1000, 500, 1000, 500, zoomed)
        assertEquals(250f, projection.left + 250 * projection.scale, .01f)
        assertEquals(100f, projection.top + 100 * projection.scale, .01f)
        val output = OcrImageGeometry.output(1000, 500, 1000, 500, zoomed)
        assertEquals(450, output.width)
        assertEquals(70, output.height)
    }

    @Test fun extremeShapesAndFreePanningKeepFiniteBoundedOutput() {
        for ((w, h) in listOf(1 to 1, 2000 to 50, 50 to 2000)) {
            val output = OcrImageGeometry.output(w, h, 800, 200, OcrImageTransform(.25f, 10000f, -10000f))
            assertTrue(output.width in 1..1600)
            assertTrue(output.height > 0)
            assertTrue(output.left.isFinite() && output.top.isFinite() && output.scale > 0)
        }
        assertEquals(8f, OcrImageTransform().gesture(100, 100, 0f, 0f, 0f, 0f, 100f).zoom, 0f)
        assertEquals(.25f, OcrImageTransform().gesture(100, 100, 0f, 0f, 0f, 0f, .01f).zoom, 0f)
    }
}
