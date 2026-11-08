package com.kingzcheung.xime.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** URI only: the IME owns the bounded decoded image, not an Activity or a global bitmap. */
internal object OcrGallerySelection { val uri = MutableStateFlow<Uri?>(null) }

internal fun loadOcrGalleryImage(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val maxSide = maxOf(info.size.width, info.size.height)
        val ratio = minOf(1f, 2048f / maxSide)
        decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

internal fun cropGalleryStrip(image: Bitmap, vw: Int, vh: Int, transform: OcrImageTransform,
                              frame: OcrScanFrame = OcrScanGeometry.TEXT): Bitmap {
    val output = OcrImageGeometry.output(image.width, image.height, vw, vh, transform, frame)
    return Bitmap.createBitmap(output.width, output.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply { setScale(output.scale, output.scale); postTranslate(output.left, output.top) }
        canvas.drawBitmap(image, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
