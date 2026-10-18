package com.kingzcheung.xime.clipboard.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.PerspectiveTransform
import com.google.zxing.qrcode.QRCodeWriter
import com.kingzcheung.xime.camera.CameraLuminance
import java.nio.ByteBuffer
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PairingQrCameraTest {
    private val compact = "cyime://pair?v=2&c=AgHAqAECSTQAESIzRFVmd4iZqrvM3e7_AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8RIjNEVWZ3iJmqu8zd7v8AAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8AAAAAdzWUAA"
    private fun source(bytes: ByteArray, w: Int, h: Int) = PlanarYUVLuminanceSource(bytes, w, h, 0, 0, w, h, false)

    @Test fun cameraPlaneWithPaddingNoiseShadowAndPerspectiveKeepsPairingPayload() {
        val size = 360
        val code = QRCodeWriter().encode(compact, BarcodeFormat.QR_CODE, size, size)
        val width = 640; val height = 480; val stride = width + 24
        val transform = PerspectiveTransform.quadrilateralToQuadrilateral(
            130f, 45f, 500f, 80f, 470f, 430f, 110f, 385f,
            0f, 0f, size.toFloat(), 0f, size.toFloat(), size.toFloat(), 0f, size.toFloat())
        val random = Random(51)
        val plane = ByteBuffer.allocate(stride * height)
        for (y in 0 until height) for (x in 0 until width) {
            val point = floatArrayOf(x.toFloat(), y.toFloat()); transform.transformPoints(point)
            val sx = point[0].toInt(); val sy = point[1].toInt()
            val dark = sx in 0 until size && sy in 0 until size && code[sx, sy]
            val value = (if (dark) 65 else 165) + x * 35 / width + random.nextInt(-10, 11)
            plane.put(y * stride + x, value.toByte())
        }
        val frame = CameraLuminance.read(plane, stride, 1, 0, 0, width, height)
        assertEquals(compact, PairingQrDecoder.decode(source(frame.bytes, frame.width, frame.height)))
    }

    @Test fun rotationsAndInvertedScreenCodeDecodeWithoutJpegOrBitmap() {
        val size = 360
        val matrix = QRCodeWriter().encode(compact, BarcodeFormat.QR_CODE, size, size)
        for (rotation in 0..3) for (inverted in listOf(false, true)) {
            val data = ByteArray(size * size) { i ->
                val x = i % size; val y = i / size
                val dark = when (rotation) {
                    0 -> matrix[x, y]; 1 -> matrix[y, size - 1 - x]
                    2 -> matrix[size - 1 - x, size - 1 - y]; else -> matrix[size - 1 - y, x]
                }
                (if (dark xor inverted) 0 else 255).toByte()
            }
            assertEquals("rotation=$rotation inverted=$inverted", compact, PairingQrDecoder.decode(source(data, size, size)))
        }
    }

    @Test fun blankAndNoisyFramesNeverProducePairingOffers() {
        val random = Random(123)
        for (base in listOf(0, 12, 127, 243, 255)) {
            val data = ByteArray(320 * 240) { (base + random.nextInt(-8, 9)).coerceIn(0, 255).toByte() }
            assertNull(PairingQrDecoder.scan(source(data, 320, 240)))
        }
    }

    @Test fun foreignAndMalformedCodesAreDistinguishedWithoutStartingConnections() {
        for ((text, expected) in listOf("https://example.org" to PairingQrDecoder.Result.OtherCode,
            "cyime://pair?v=2&c=broken" to PairingQrDecoder.Result.InvalidPairingCode)) {
            val size = 320; val code = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val data = ByteArray(size * size) { if (code[it % size, it / size]) 0 else 255.toByte() }
            assertEquals(expected, PairingQrDecoder.scan(source(data, size, size)))
            assertNull(PairingQrDecoder.decode(source(data, size, size)))
        }
    }
}
