package com.kingzcheung.xime.settings

import java.util.Locale
import kotlin.math.roundToInt

/**
 * What enabled cell dictionaries cost typing, from their total word count, before they are applied.
 *
 * Two curves (words → how much slower the slowest keys get, in percent):
 * - measured on this device or the same model by `tools/ime_lab/dict_cost_bench.py` (real Sogou lists of
 *   about 2 thousand to 200 thousand words, stored with the switch costs in perf-costs.json, see
 *   [PerformanceCosts.Device.dictionaries]); used whenever there is one;
 * - otherwise [Curve.DESKTOP]: measured 2026-10-24 on the desktop copy of the engine (26-key full pinyin,
 *   tune set + must-pass rows) with a worst-case dictionary of made-up words absent from the built-in one:
 *   the slowest 5% of keys took 14% longer at 50,000 words, 21% at 100,000 and 35% at 250,000. Real word
 *   lists overlap the built-in dictionary and cost less, so this is an upper bound.
 */
object CellDictionaryCost {
    /** Above this many enabled words the desktop curve is past [HEAVY_PERCENT]. */
    const val WARN_ENTRIES = 50_000

    /** Linear between the points, which start at 0 words; past the last point it grows in proportion. */
    class Curve(points: List<Pair<Int, Double>>, val measuredOnDevice: Boolean = false) {
        private val points = (listOf(0 to 0.0) + points.filter { it.first > 0 }).sortedBy { it.first }.distinctBy { it.first }

        fun slowdown(entries: Int): Double {
            if (entries <= 0 || points.size < 2) return 0.0
            val (lastCount, lastValue) = points.last()
            if (entries >= lastCount) return lastValue * entries / lastCount
            val upper = points.indexOfFirst { it.first >= entries }
            val (x0, y0) = points[upper - 1]
            val (x1, y1) = points[upper]
            return y0 + (y1 - y0) * (entries - x0) / (x1 - x0)
        }

        companion object {
            val DESKTOP = Curve(listOf(50_000 to 14.0, 100_000 to 21.0, 250_000 to 35.0))

            /**
             * The device's own curve: extra time of the slowest 5% of keys over the run with no dictionary on
             * (the median when the run has no p95); null when that device has no dictionary measurement.
             */
            fun fromDevice(device: PerformanceCosts.Device): Curve? {
                val base = device.dictionaryBaseline ?: device.baseline
                val p95 = base.keyP95Ms
                val points = device.dictionaries.mapNotNull { point ->
                    val percent = if (p95 != null && p95 > 0 && point.keyP95Ms != null) point.keyP95Ms / p95 * 100
                    else point.keyMs?.takeIf { base.keyMs > 0 }?.let { it / base.keyMs * 100 }
                    percent?.let { point.words to it.coerceAtLeast(0.0) }
                }
                return if (points.isEmpty()) null else Curve(points, measuredOnDevice = true)
            }
        }
    }

    /** Extra time of the slowest keys, in percent, rounded. */
    fun slowdownPercent(entries: Int, curve: Curve = Curve.DESKTOP): Int = slowdown(entries, curve).roundToInt()

    /** [slowdownPercent] before rounding, so a small dictionary's share is not shown as 0. */
    fun slowdown(entries: Int, curve: Curve = Curve.DESKTOP): Double = curve.slowdown(entries)

    /** What one dictionary adds on top of the other dictionaries that are on (the curve is not a straight line). */
    fun share(otherEntries: Int, entries: Int, curve: Curve = Curve.DESKTOP): Double =
        curve.slowdown(otherEntries + entries) - curve.slowdown(otherEntries.coerceAtLeast(0))

    /** 0.43 → 0.4%，3.0 → 3%，12.6 → 13%；不到 0.1 写「<0.1%」。 */
    fun format(percent: Double): String = when {
        percent < 0.05 -> "<0.1%"
        percent < 10 -> "%.1f".format(Locale.ROOT, percent).removeSuffix(".0") + "%"
        else -> "${percent.roundToInt()}%"
    }

    /** 慢多少算正常、偏慢、很慢（用户 11-02：不设上限，但超出正常要用颜色提醒）。 */
    enum class Level { NORMAL, NOTICEABLE, HEAVY }

    /** 不到 5% 算正常：最坏估计下约 1.8 万词以内，搜狗一个专业词库通常几千词。 */
    const val NOTICEABLE_PERCENT = 5.0

    /** 超过 14%（最坏估计下 5 万词那一点）算很慢，应用前要确认。 */
    const val HEAVY_PERCENT = 14.0

    fun level(percent: Double): Level = when {
        percent > HEAVY_PERCENT -> Level.HEAVY
        percent >= NOTICEABLE_PERCENT -> Level.NOTICEABLE
        else -> Level.NORMAL
    }
}
