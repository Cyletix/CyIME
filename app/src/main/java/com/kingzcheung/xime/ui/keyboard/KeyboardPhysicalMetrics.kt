package com.kingzcheung.xime.ui.keyboard

/** Physical units use the current display's X dpi and the renderer's px-per-dp. */
internal data class KeyboardPhysicalMetrics private constructor(
    val pixelsPerDp: Float,
    val xDpi: Float,
) {
    fun millimetersFromDp(dp: Float): Float = (dp.toDouble() * pixelsPerDp * 25.4 / xDpi).toFloat()
    fun dpFromMillimeters(mm: Double): Float = (mm * xDpi / 25.4 / pixelsPerDp).toFloat()

    companion object {
        fun from(pixelsPerDp: Float, xDpi: Float): KeyboardPhysicalMetrics? {
            if (!pixelsPerDp.isFinite() || pixelsPerDp <= 0f || !xDpi.isFinite() || xDpi <= 0f) return null
            val value = KeyboardPhysicalMetrics(pixelsPerDp, xDpi)
            val pitch = value.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
            // The renderer cannot represent a cell below one dp; overflow is not a usable measurement.
            return value.takeIf { pitch.isFinite() && pitch >= 1f && (16f + 10f * pitch).isFinite() }
        }
    }
}

/**
 * SM-X800, 2026-10-08: read the user's 1005dp preset, then the user requested
 * a rounded 1000dp automatic default. Keep the saved 1005dp preset untouched.
 * (1000dp - 2 * 8dp gutters) / 10 * 1.875px/dp / 266.471dpi * 25.4.
 * This user's calibration, not an anthropometric estimate.
 */
internal const val CALIBRATED_QWERTY_PITCH_MM = 17.586529115738674
internal const val CALIBRATED_QWERTY_REFERENCE_CELL_DP = 98.4f

private val standardQwertyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    .map { row -> row.map(Char::toString) }

internal fun hasStandardQwertyRows(rows: List<List<String>>): Boolean = rows == standardQwertyRows
