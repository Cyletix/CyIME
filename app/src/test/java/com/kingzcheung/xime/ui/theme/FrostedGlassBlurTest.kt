package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedGlassBlurTest {
    @Test
    fun zeroRadiusPreservesImage() {
        val pixels = intArrayOf(0xff102030.toInt(), 0xffaabbcc.toInt())
        val original = pixels.copyOf()
        blurFrostedPixels(pixels, 2, 1, 0f)
        assertArrayEquals(original, pixels)
    }

    @Test
    fun uniformColorStaysOpaqueIncludingEdges() {
        val original = IntArray(3 * 7) { 0xff28517a.toInt() }
        val pixels = original.copyOf()
        blurFrostedPixels(pixels, 3, 7, 24f)
        assertArrayEquals(original, pixels)
    }

    @Test
    fun brightDetailSpreadsSymmetricallyWithoutChangingOtherChannels() {
        val pixels = IntArray(15 * 15) { 0xff000000.toInt() }
        pixels[7 * 15 + 7] = 0xffff0000.toInt()
        blurFrostedPixels(pixels, 15, 15, 1.5f)
        val center = pixels[7 * 15 + 7] ushr 16 and 0xff
        assertTrue(center in 1..254)
        assertTrue((pixels[7 * 15 + 6] ushr 16 and 0xff) > 0)
        for (y in 0 until 15) for (x in 0 until 15) {
            assertEquals(pixels[y * 15 + x], pixels[y * 15 + 14 - x])
            assertEquals(pixels[y * 15 + x], pixels[(14 - y) * 15 + x])
            assertEquals(0, pixels[y * 15 + x] and 0xffff)
            assertEquals(255, pixels[y * 15 + x] ushr 24)
        }
    }
}
