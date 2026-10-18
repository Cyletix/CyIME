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

/** 分体候选窗口：[range] 为显示的候选，前 [leftCount] 个排在中缝左边。 */
internal data class CandidateWindowSplit(val range: IntRange, val leftCount: Int)

/** 分体候选：从窗口起点先填满中缝左侧，再接着填右侧；焦点不在其中时从焦点重新开始。 */
internal fun splitCandidateWindow(
    total: Int, highlighted: Int, leftAvailable: Int, rightAvailable: Int, spacing: Int,
    windowStart: Int, widthAt: (Int) -> Int,
): CandidateWindowSplit {
    if (total <= 0) return CandidateWindowSplit(IntRange.EMPTY, 0)
    val focused = highlighted.takeIf { it in 0 until total } ?: 0
    fun fit(from: Int, available: Int): Int {
        var used = 0
        var count = 0
        while (from + count < total) {
            val next = used + (if (count == 0) 0 else spacing) + widthAt(from + count)
            if (next > available) break
            used = next
            count++
        }
        return count
    }
    fun fill(start: Int): CandidateWindowSplit {
        val left = fit(start, leftAvailable)
        val right = if (start + left < total) fit(start + left, rightAvailable) else 0
        val end = (start + (left + right).coerceAtLeast(1)).coerceAtMost(total)
        return CandidateWindowSplit(start until end, left)
    }
    val start = windowStart.coerceIn(0, total - 1)
    val window = fill(if (focused < start) focused else start)
    return if (focused in window.range) window else fill(focused)
}
