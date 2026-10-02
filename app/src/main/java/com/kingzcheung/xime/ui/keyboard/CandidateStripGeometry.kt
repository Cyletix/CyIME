package com.kingzcheung.xime.ui.keyboard

/** Keep a contiguous prefix, no clipped last candidate. A single oversized first word is ellipsized. */
internal fun candidatePrefixCount(widths: List<Int>, available: Int, spacing: Int): Int =
    candidatePrefixCount(widths.size, available, spacing) { widths[it] }

/** Keep the current window while focus fits; crossing an edge scrolls toward that edge.
 * Indices remain engine/display indices, never positions in the visible window. */
internal inline fun candidateWindow(total: Int, highlighted: Int, available: Int, spacing: Int,
    windowStart: Int = 0,
    widthAt: (Int) -> Int): IntRange {
    if (total <= 0 || available <= 0) return IntRange.EMPTY
    val focused = highlighted.takeIf { it in 0 until total } ?: 0
    val start = windowStart.coerceIn(0, total - 1)
    if (focused < start) {
        val count = candidatePrefixCount(total - focused, available, spacing) { widthAt(focused + it) }
        return focused until focused + count
    }
    val count = candidatePrefixCount(total - start, available, spacing) { widthAt(start + it) }
    if (focused < start + count) return start until start + count

    // Fill backwards from the next focused word, so it stays last instead of jumping to first.
    var first = focused
    var used = widthAt(focused)
    while (first > 0) {
        val next = used + spacing + widthAt(first - 1)
        if (next > available) break
        used = next
        first--
    }
    return first..focused
}

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
