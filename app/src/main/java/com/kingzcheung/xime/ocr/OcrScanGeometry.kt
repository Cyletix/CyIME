package com.kingzcheung.xime.ocr

import kotlin.math.max
import kotlin.math.roundToInt

internal data class OcrCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/** The same normalized strip drives the overlay and the actual camera crop. */
internal object OcrScanGeometry {
    const val LEFT = .05f
    const val TOP = .36f
    const val RIGHT = .95f
    const val BOTTOM = .64f

    fun crop(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): OcrCrop {
        require(imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val offsetX = (imageWidth * scale - viewWidth) / 2
        val offsetY = (imageHeight * scale - viewHeight) / 2
        val left = ((viewWidth * LEFT + offsetX) / scale).roundToInt().coerceIn(0, imageWidth - 1)
        val top = ((viewHeight * TOP + offsetY) / scale).roundToInt().coerceIn(0, imageHeight - 1)
        val right = ((viewWidth * RIGHT + offsetX) / scale).roundToInt().coerceIn(left + 1, imageWidth)
        val bottom = ((viewHeight * BOTTOM + offsetY) / scale).roundToInt().coerceIn(top + 1, imageHeight)
        return OcrCrop(left, top, right - left, bottom - top)
    }
}
