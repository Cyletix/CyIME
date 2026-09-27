package com.kingzcheung.xime.ui.keyboard

internal data class T9CandidateMetrics(val rowHeightDp: Float, val fontSizeSp: Float)

/** Stable four-row rhythm; a long valid syllable reserves the same width for every item. */
internal fun t9CandidateMetrics(widthDp: Float, panelHeightDp: Float,
    fontScale: Float, textScale: Float, contentScale: Float): T9CandidateMetrics {
    val rowHeight = (panelHeightDp / 4f).coerceAtLeast(32f)
    // Width is fitted against the real font in Compose, not an ASCII-width estimate.
    val fontSize = minOf(15f * contentScale * textScale,
        (rowHeight - 6f).coerceAtLeast(1f) / (fontScale.coerceAtLeast(0.1f) * 1.4f),
        (widthDp - 6f).coerceAtLeast(1f) / fontScale.coerceAtLeast(0.1f))
    return T9CandidateMetrics(rowHeight, fontSize)
}
