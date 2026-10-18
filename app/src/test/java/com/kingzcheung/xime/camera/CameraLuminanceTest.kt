package com.kingzcheung.xime.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class CameraLuminanceTest {
    @Test fun readsVisibleCropWithBufferOffsetRowPaddingAndPixelStride() {
        for (pixelStride in listOf(1, 2)) {
            val stride = 8 * pixelStride + 5
            val buffer = ByteBuffer.allocate(7 + stride * 6)
            for (y in 0 until 6) for (x in 0 until 8) buffer.put(7 + y * stride + x * pixelStride, (y * 10 + x).toByte())
            buffer.position(7)
            val result = CameraLuminance.read(buffer, stride, pixelStride, 2, 1, 4, 3)
            assertEquals(7, buffer.position())
            assertEquals(4, result.width); assertEquals(3, result.height)
            assertArrayEquals(byteArrayOf(12, 13, 14, 15, 22, 23, 24, 25, 32, 33, 34, 35), result.bytes)
        }
    }

    @Test fun capsAnalysisPixelsEvenWhenCameraIgnoresPreferredResolution() {
        val buffer = ByteBuffer.allocate(4000 * 3000)
        val result = CameraLuminance.read(buffer, 4000, 1, 0, 0, 4000, 3000)
        assertEquals(1000, result.width); assertEquals(750, result.height)
        assertEquals(750000, result.bytes.size)
    }

    @Test fun acceptsLastRowWithoutTrailingPaddingButRejectsMissingActualPixels() {
        val buffer = ByteBuffer.allocate(12 + 8)
        assertEquals(16, CameraLuminance.read(buffer, 12, 1, 0, 0, 8, 2).bytes.size)
        buffer.limit(19)
        assertTrue(runCatching { CameraLuminance.read(buffer, 12, 1, 0, 0, 8, 2) }.isFailure)
    }
}
