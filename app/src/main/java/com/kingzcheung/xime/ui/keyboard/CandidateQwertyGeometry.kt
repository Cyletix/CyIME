package com.kingzcheung.xime.ui.keyboard

/** Geometry of the typing layout behind the expanded candidate page. Fractions exclude its gutter. */
data class CandidateQwertyGeometry(
    val columns: Float,
    val split: Boolean,
    val modeFraction: Float,
    val deleteFraction: Float,
    val enterFraction: Float,
) {
    val widthFraction: Float get() = if (split) .9f else 1f
    val innerInsetDp: Float get() = if (split) 4f else 0f
    val deleteEndInsetDp: Float get() = 0f
    fun deleteWidth(widthDp: Float): Float = (widthDp * enterFraction).coerceAtLeast(1f)
}

internal fun candidateQwertyGeometry(
    rows: List<List<String>>,
    fourteenKey: Boolean,
    custom: Boolean,
    split: Boolean,
): CandidateQwertyGeometry {
    val columns = letterKeyboardColumns(rows, fourteenKey)
    val bottom = letterKeyboardBottomRow(rows, fourteenKey)
    val right = if (split) .45f * 1.2f / 5.2f else bottom.enter / bottom.total
    return CandidateQwertyGeometry(columns, split,
        modeFraction = if (split) .16f else bottom.mode / bottom.total,
        deleteFraction = right,
        enterFraction = right)
}
