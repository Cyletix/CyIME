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
    val deleteEndInsetDp: Float get() = if (split) 12f else 0f
    fun deleteWidth(widthDp: Float): Float =
        (widthDp * deleteFraction - if (split) 12f / 5f else 0f).coerceAtLeast(1f)
}

internal fun candidateQwertyGeometry(
    rows: List<List<String>>,
    fourteenKey: Boolean,
    custom: Boolean,
    split: Boolean,
): CandidateQwertyGeometry {
    val columns = if (fourteenKey) 10f else rows.firstOrNull()?.size?.toFloat() ?: 10f
    val bottom = if (fourteenKey || columns == 10f) QwertyBottomRowWeights.Standard else QwertyBottomRowWeights.Legacy
    val side = standardLetterRowGeometry(rows, 1f, custom, split)?.outerKeyWeight ?: 1.4f
    return CandidateQwertyGeometry(columns, split,
        modeFraction = if (split) .16f else bottom.mode / bottom.total,
        deleteFraction = if (split) .45f / 5f else side / (7f + side * 2f),
        enterFraction = if (split) .45f * 1.2f / 5.2f else bottom.enter / bottom.total)
}
