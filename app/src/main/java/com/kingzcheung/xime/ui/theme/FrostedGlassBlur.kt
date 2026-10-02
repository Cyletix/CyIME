package com.kingzcheung.xime.ui.theme

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Three separable box passes approximate a Gaussian blur in O(width * height).
 * The input is an opaque, already-composited background. Clamped edges prevent
 * dark/transparent fringes; no key or text pixels are supplied to this function.
 */
internal fun blurFrostedPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    sigma: Float,
    checkCancelled: () -> Unit = {},
) {
    require(width > 0 && height > 0 && pixels.size == width * height)
    if (!sigma.isFinite() || sigma <= 0f) return
    val radius = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt()
        .coerceIn(0, maxOf(width, height))
    if (radius == 0) return
    val temporary = IntArray(pixels.size)
    repeat(3) {
        checkCancelled()
        boxBlurPass(pixels, temporary, width, height, radius, horizontal = true)
        checkCancelled()
        boxBlurPass(temporary, pixels, width, height, radius, horizontal = false)
    }
}

private fun boxBlurPass(
    source: IntArray,
    destination: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val lineCount = if (horizontal) height else width
    val lineLength = if (horizontal) width else height
    val stride = if (horizontal) 1 else width
    val divisor = radius * 2 + 1
    for (line in 0 until lineCount) {
        val start = if (horizontal) line * width else line
        val first = source[start]
        var red = (first ushr 16 and 0xff) * (radius + 1)
        var green = (first ushr 8 and 0xff) * (radius + 1)
        var blue = (first and 0xff) * (radius + 1)
        for (offset in 1..minOf(radius, lineLength - 1)) {
            val color = source[start + offset * stride]
            red += color ushr 16 and 0xff
            green += color ushr 8 and 0xff
            blue += color and 0xff
        }
        val repeatedLast = (radius - lineLength + 1).coerceAtLeast(0)
        val last = source[start + (lineLength - 1) * stride]
        red += (last ushr 16 and 0xff) * repeatedLast
        green += (last ushr 8 and 0xff) * repeatedLast
        blue += (last and 0xff) * repeatedLast
        for (position in 0 until lineLength) {
            destination[start + position * stride] = 0xff000000.toInt() or
                (((red + divisor / 2) / divisor) shl 16) or
                (((green + divisor / 2) / divisor) shl 8) or ((blue + divisor / 2) / divisor)
            val leaving = source[start + (position - radius).coerceIn(0, lineLength - 1) * stride]
            val entering = source[start + (position + radius + 1).coerceIn(0, lineLength - 1) * stride]
            red += (entering ushr 16 and 0xff) - (leaving ushr 16 and 0xff)
            green += (entering ushr 8 and 0xff) - (leaving ushr 8 and 0xff)
            blue += (entering and 0xff) - (leaving and 0xff)
        }
    }
}
