package com.kingzcheung.xime.ui.keyboard

/** Keep a contiguous prefix, no clipped last candidate. A single oversized first word is ellipsized. */
internal fun candidatePrefixCount(widths: List<Int>, available: Int, spacing: Int): Int {
    if (available <= 0 || widths.isEmpty()) return 0
    var used = 0
    var count = 0
    for (width in widths) {
        val next = used + (if (count == 0) 0 else spacing) + width
        if (next > available) break
        used = next
        count++
    }
    return count.coerceAtLeast(1)
}

/** Remove the rendered prefix only; global engine indices survive filtering/reflow unchanged. */
internal fun remainingCandidates(entries: List<CandidateEntry>, visible: List<String>): List<CandidateEntry> =
    if (entries.take(visible.size).map { it.text } == visible) entries.drop(visible.size) else entries
