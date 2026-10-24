package com.kingzcheung.xime.settings

import kotlin.math.roundToInt

/**
 * What enabled cell dictionaries cost typing, estimated from their total word count before they are applied.
 *
 * Measured 2026-10-24 on the desktop copy of the engine (26-key full pinyin, tune set + must-pass rows) with a
 * worst-case dictionary of made-up words absent from the built-in one: the slowest 5% of keys took 14% longer
 * at 50,000 words, 21% at 100,000 and 35% at 250,000; 5%, 8.5% and 13% of inputs got such a word in their first
 * five candidates. Real word lists overlap the built-in dictionary and cost less, so this is an upper bound.
 */
object CellDictionaryCost {
    /** Above this many enabled words the page warns before applying. */
    const val WARN_ENTRIES = 50_000

    private val slowdown = listOf(0 to 0.0, 50_000 to 14.0, 100_000 to 21.0, 250_000 to 35.0)

    /** Worst-case extra time of the slowest keys, in percent (linear between the measured points). */
    fun slowdownPercent(entries: Int): Int {
        if (entries <= 0) return 0
        val (lastCount, lastValue) = slowdown.last()
        if (entries >= lastCount) return (lastValue * entries / lastCount).roundToInt()
        val upper = slowdown.indexOfFirst { it.first >= entries }
        val (x0, y0) = slowdown[upper - 1]
        val (x1, y1) = slowdown[upper]
        return (y0 + (y1 - y0) * (entries - x0) / (x1 - x0)).roundToInt()
    }

    /** One line for the page: total words and the worst-case slowdown. */
    fun describe(entries: Int): String = when {
        entries <= 0 -> "未开启补充词库"
        else -> "共 $entries 条 · 最坏情况下最慢的按键约慢 ${slowdownPercent(entries)}%"
    }
}
