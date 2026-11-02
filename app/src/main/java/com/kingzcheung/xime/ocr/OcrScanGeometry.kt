package com.kingzcheung.xime.ocr

import kotlin.math.max
import kotlin.math.roundToInt

internal data class OcrCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/** Normalized scan frame in view coordinates. */
internal data class OcrScanFrame(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The same normalized strip drives the overlay and the actual camera crop. */
internal object OcrScanGeometry {
    const val LEFT = .05f
    const val TOP = .36f
    const val RIGHT = .95f
    const val BOTTOM = .64f
    val TEXT = OcrScanFrame(LEFT, TOP, RIGHT, BOTTOM)

    /**
     * A table needs most of the panel: everything between the hint line at the top and the floating
     * controls at the bottom (reserves in pixels). Never smaller than the text strip.
     */
    fun table(viewHeight: Int, topReserve: Float, bottomReserve: Float): OcrScanFrame {
        if (viewHeight <= 0) return TEXT
        return OcrScanFrame(.04f, (topReserve / viewHeight).coerceIn(.02f, TOP),
            .96f, (1 - bottomReserve / viewHeight).coerceIn(BOTTOM, .98f))
    }

    fun crop(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int, frame: OcrScanFrame = TEXT): OcrCrop {
        require(imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val offsetX = (imageWidth * scale - viewWidth) / 2
        val offsetY = (imageHeight * scale - viewHeight) / 2
        val left = ((viewWidth * frame.left + offsetX) / scale).roundToInt().coerceIn(0, imageWidth - 1)
        val top = ((viewHeight * frame.top + offsetY) / scale).roundToInt().coerceIn(0, imageHeight - 1)
        val right = ((viewWidth * frame.right + offsetX) / scale).roundToInt().coerceIn(left + 1, imageWidth)
        val bottom = ((viewHeight * frame.bottom + offsetY) / scale).roundToInt().coerceIn(top + 1, imageHeight)
        return OcrCrop(left, top, right - left, bottom - top)
    }
}
