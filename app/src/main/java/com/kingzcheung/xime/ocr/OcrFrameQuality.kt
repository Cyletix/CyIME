package com.kingzcheung.xime.ocr

import android.graphics.Bitmap
import kotlin.math.abs

/** Reject empty/noisy exposure before compression, IPC and native recognition. No character blacklist. */
internal object OcrFrameQuality {
    enum class Verdict { READY, DARK, LOW_DETAIL }

    fun assess(bitmap: Bitmap): Verdict {
        val scale = minOf(1f, 480f / bitmap.width, 240f / bitmap.height)
        val sample = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        return try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            assess(pixels, sample.width, sample.height)
        } finally { if (sample !== bitmap) sample.recycle() }
    }

    fun assess(pixels: IntArray, width: Int, height: Int): Verdict {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val histogram = IntArray(256)
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            ((77 * (p shr 16 and 255) + 150 * (p shr 8 and 255) + 29 * (p and 255)) shr 8)
                .also { histogram[it]++ }
        }
        fun percentile(fraction: Float): Int {
            val rank = (pixels.size * fraction).toInt().coerceAtLeast(1)
            var sum = 0
            return histogram.indices.first { sum += histogram[it]; sum >= rank }
        }
        // Ignore isolated sensor hot pixels, while retaining small real text on dark backgrounds.
        val low = percentile(.002f)
        val high = percentile(.998f)
        if (high < 40) return Verdict.DARK
        if (high - low < 24) return Verdict.LOW_DETAIL
        var edges = 0
        for (y in 1 until height) for (x in 1 until width) {
            val i = y * width + x
            if (abs(gray[i] - gray[i - 1]) >= 20 || abs(gray[i] - gray[i - width]) >= 20) edges++
        }
        return if (edges < pixels.size * .003f) Verdict.LOW_DETAIL else Verdict.READY
    }
}

/** Live text needs two agreeing frames; a new/empty scene immediately clears the old result. */
internal class OcrPreviewStability {
    private var previous = ""
    fun update(text: String): String {
        val next = text.trim()
        val stable = next.isNotEmpty() && next == previous
        previous = next
        return if (stable) next else ""
    }
}
