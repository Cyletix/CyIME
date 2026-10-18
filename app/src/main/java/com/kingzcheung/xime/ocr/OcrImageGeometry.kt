package com.kingzcheung.xime.ocr

import kotlin.math.min
import kotlin.math.roundToInt

internal data class OcrImageTransform(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    fun gesture(viewWidth: Int, viewHeight: Int, x: Float, y: Float, dx: Float, dy: Float, zoomChange: Float): OcrImageTransform {
        val nextZoom = (zoom * zoomChange).coerceIn(.25f, 8f)
        val ratio = nextZoom / zoom
        return OcrImageTransform(nextZoom, panX * ratio + (x - viewWidth / 2f) * (1 - ratio) + dx,
            panY * ratio + (y - viewHeight / 2f) * (1 - ratio) + dy)
    }
}

internal data class OcrImageProjection(val scale: Float, val left: Float, val top: Float)
internal data class OcrImageOutput(val width: Int, val height: Int, val scale: Float, val left: Float, val top: Float)

/** The guide is fixed in view coordinates. Only the image's affine transform changes. */
internal object OcrImageGeometry {
    fun projection(iw: Int, ih: Int, vw: Int, vh: Int, transform: OcrImageTransform): OcrImageProjection {
        require(iw > 0 && ih > 0 && vw > 0 && vh > 0)
        val scale = min(vw.toFloat() / iw, vh.toFloat() / ih) * transform.zoom
        return OcrImageProjection(scale, (vw - iw * scale) / 2 + transform.panX,
            (vh - ih * scale) / 2 + transform.panY)
    }

    fun output(iw: Int, ih: Int, vw: Int, vh: Int, transform: OcrImageTransform): OcrImageOutput {
        val image = projection(iw, ih, vw, vh, transform)
        val frameWidth = vw * (OcrScanGeometry.RIGHT - OcrScanGeometry.LEFT)
        val frameHeight = vh * (OcrScanGeometry.BOTTOM - OcrScanGeometry.TOP)
        val width = (frameWidth / image.scale).roundToInt().coerceIn(1, 1600)
        val factor = width / frameWidth
        return OcrImageOutput(width, (frameHeight * factor).roundToInt().coerceAtLeast(1),
            image.scale * factor, (image.left - vw * OcrScanGeometry.LEFT) * factor,
            (image.top - vh * OcrScanGeometry.TOP) * factor)
    }
}
