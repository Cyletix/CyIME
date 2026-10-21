package com.kingzcheung.xime.rime

/**
 * How a corrected candidate's pinyin differs from what was typed, letter by letter.
 *
 * The engine reports a corrected candidate's syllables as "typed=correct" (corrected) or "typed"
 * (kept as typed), space separated. Each corrected syllable is aligned with optimal string
 * alignment (insert, delete, replace, swap of two neighbours, each costing 1). Pure and
 * platform free so the Windows client can share the same rule.
 */
sealed interface CorrectionMark {
    /** Typed and correct agree. */
    data class Keep(val letter: Char) : CorrectionMark
    /** Typed one letter too many: shown struck through. */
    data class Extra(val letter: Char) : CorrectionMark
    /** Left a letter out: shown added in red. */
    data class Missing(val letter: Char) : CorrectionMark
    /** Typed a wrong letter: struck through, the right one shown with it. */
    data class Replace(val typed: Char, val correct: Char) : CorrectionMark
    /** Typed two neighbours the wrong way round; [correct] is the right order. */
    data class Swap(val correct: String) : CorrectionMark
    /** Boundary between syllables. */
    data object Gap : CorrectionMark
}

object CorrectionMarks {
    fun parse(marks: String): List<CorrectionMark> {
        if (marks.isBlank()) return emptyList()
        val result = ArrayList<CorrectionMark>()
        marks.trim().split(' ').filter { it.isNotEmpty() }.forEachIndexed { i, syllable ->
            if (i > 0) result += CorrectionMark.Gap
            val eq = syllable.indexOf('=')
            if (eq < 0) syllable.forEach { result += CorrectionMark.Keep(it) }
            else result += align(syllable.substring(0, eq), syllable.substring(eq + 1))
        }
        return result
    }

    fun align(typed: String, correct: String): List<CorrectionMark> {
        val n = typed.length
        val m = correct.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            var best = minOf(
                d[i - 1][j] + 1,
                d[i][j - 1] + 1,
                d[i - 1][j - 1] + if (typed[i - 1] == correct[j - 1]) 0 else 1,
            )
            if (i > 1 && j > 1 && typed[i - 1] == correct[j - 2] && typed[i - 2] == correct[j - 1] &&
                typed[i - 1] != typed[i - 2]
            ) best = minOf(best, d[i - 2][j - 2] + 1)
            d[i][j] = best
        }
        val out = ArrayList<CorrectionMark>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && typed[i - 1] == correct[j - 1] && d[i][j] == d[i - 1][j - 1] -> {
                    out += CorrectionMark.Keep(typed[i - 1]); i--; j--
                }
                i > 1 && j > 1 && typed[i - 1] == correct[j - 2] && typed[i - 2] == correct[j - 1] &&
                    typed[i - 1] != typed[i - 2] && d[i][j] == d[i - 2][j - 2] + 1 -> {
                    out += CorrectionMark.Swap(correct.substring(j - 2, j)); i -= 2; j -= 2
                }
                i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1 -> {
                    out += CorrectionMark.Replace(typed[i - 1], correct[j - 1]); i--; j--
                }
                j > 0 && d[i][j] == d[i][j - 1] + 1 -> {
                    out += CorrectionMark.Missing(correct[j - 1]); j--
                }
                else -> {
                    out += CorrectionMark.Extra(typed[i - 1]); i--
                }
            }
        }
        return out.asReversed()
    }
}
