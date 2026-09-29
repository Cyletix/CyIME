package com.kingzcheung.xime.ui.keyboard

/** Keep a contiguous prefix, no clipped last candidate. A single oversized first word is ellipsized. */
internal fun candidatePrefixCount(widths: List<Int>, available: Int, spacing: Int): Int =
    candidatePrefixCount(widths.size, available, spacing) { widths[it] }

/** Measure only the visible prefix and one overflow item, regardless of engine page size. */
internal inline fun candidatePrefixCount(total: Int, available: Int, spacing: Int, widthAt: (Int) -> Int): Int {
    if (available <= 0 || total == 0) return 0
    var used = 0
    var count = 0
    while (count < total) {
        val next = used + (if (count == 0) 0 else spacing) + widthAt(count)
        if (next > available) break
        used = next
        count++
    }
    return count.coerceAtLeast(1)
}

/** Remove the rendered prefix only; global engine indices survive filtering/reflow unchanged. */
internal fun remainingCandidates(entries: List<CandidateEntry>, visible: List<String>): List<CandidateEntry> =
    if (entries.take(visible.size).map { it.text } == visible) entries.drop(visible.size) else entries
