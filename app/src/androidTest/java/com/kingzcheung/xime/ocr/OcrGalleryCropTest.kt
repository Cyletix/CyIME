package com.kingzcheung.xime.ocr

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/** Pixel checks for the same affine map used by the preview; no camera or user album required. */
class OcrGalleryCropTest {
    @Test fun cropExcludesPixelsOutsideTheFixedMiddleFrame() {
        val image = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.RED)
        android.graphics.Canvas(image).drawRect(50f, 180f, 950f, 320f, android.graphics.Paint().apply { color = Color.GREEN })
        val crop = cropGalleryStrip(image, 1000, 500, OcrImageTransform())
        try {
            assertEquals(900, crop.width)
            assertEquals(140, crop.height)
            assertEquals(Color.GREEN, crop.getPixel(450, 70))
            assertEquals(Color.GREEN, crop.getPixel(1, 1))
            assertEquals(Color.GREEN, crop.getPixel(898, 138))
        } finally { crop.recycle(); image.recycle() }
    }

    @Test fun movingTheImageOutOfTheFrameProducesBlankPixelsNotTheWholePhoto() {
        val image = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val crop = cropGalleryStrip(image, 1000, 500, OcrImageTransform(panX = 2000f))
        try {
            assertEquals(Color.WHITE, crop.getPixel(crop.width / 2, crop.height / 2))
            assertEquals(OcrFrameQuality.Verdict.LOW_DETAIL, OcrFrameQuality.assess(crop))
        } finally { crop.recycle(); image.recycle() }
    }
}
