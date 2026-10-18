package com.kingzcheung.xime.camera

import java.nio.ByteBuffer

/** Bounded, packed Y plane. No JPEG, RGB bitmap or file round trip for live QR frames. */
internal data class CameraLuminance(val bytes: ByteArray, val width: Int, val height: Int) {
    companion object {
        fun read(
            buffer: ByteBuffer, rowStride: Int, pixelStride: Int,
            left: Int, top: Int, width: Int, height: Int, maxEdge: Int = 1280,
        ): CameraLuminance {
            require(width > 0 && height > 0 && left >= 0 && top >= 0 && maxEdge > 0)
            require(pixelStride > 0 && rowStride >= (left + width - 1) * pixelStride + 1)
            val step = ((maxOf(width, height) + maxEdge - 1) / maxEdge).coerceAtLeast(1)
            val outWidth = (width + step - 1) / step
            val outHeight = (height + step - 1) / step
            val input = buffer.duplicate()
            val start = input.position()
            val last = start.toLong() + (top + (outHeight - 1) * step).toLong() * rowStride +
                (left + (outWidth - 1) * step).toLong() * pixelStride
            require(last < input.limit()) { "Truncated camera luminance plane" }
            val bytes = ByteArray(outWidth * outHeight)
            for (y in 0 until outHeight) {
                val offset = start + (top + y * step) * rowStride + left * pixelStride
                if (step == 1 && pixelStride == 1) {
                    input.position(offset); input.get(bytes, y * outWidth, outWidth)
                } else for (x in 0 until outWidth) bytes[y * outWidth + x] = input.get(offset + x * step * pixelStride)
            }
            return CameraLuminance(bytes, outWidth, outHeight)
        }
    }
}
