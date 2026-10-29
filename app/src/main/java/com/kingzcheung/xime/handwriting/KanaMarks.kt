package com.kingzcheung.xime.handwriting

import kotlin.math.hypot
import kotlin.math.max

/**
 * 浊点（゛两短笔）和半浊点（゜小圈）的区别：模板特征把整字缩放到同一大小，最后那个小记号的形状几乎丢光，
 * ぼ/ぽ、バ/パ 常互相认错。这里只看最后一笔：首尾相接的是小圈，否则是短笔；和首选对不上时换成另一个。
 * 只在首选本来就是这十组浊音/半浊音之一时起作用。个人样本 36 份全分对（电脑上测，见开发报告）。
 */
internal object KanaMarks {
    private const val DAKUTEN = "ばびぶべぼバビブベボ"
    private const val HANDAKUTEN = "ぱぴぷぺぽパピプペポ"
    /** 最后一笔超过整字这个比例就不是记号（例如只写了一笔）。 */
    private const val MAX_MARK_SIZE = 0.35f
    /** 首尾距离小于记号大小这个比例算闭合的小圈（个人样本：小圈 ≤0.72，短笔 ≥0.95）。 */
    private const val CLOSED_GAP = 0.85f

    /** 最后一笔是不是闭合小圈；看不出是记号时返回 null。 */
    internal fun endsWithCircle(strokes: List<List<Pair<Float, Float>>>): Boolean? {
        val last = strokes.lastOrNull()?.takeIf { it.size >= 2 && strokes.size >= 2 } ?: return null
        val size = extent(listOf(last)).coerceAtLeast(1f)
        if (size > MAX_MARK_SIZE * extent(strokes)) return null
        val gap = hypot(last.last().first - last.first().first, last.last().second - last.first().second)
        return gap < CLOSED_GAP * size
    }

    /** 首选与记号形状不符时，把同一行的另一个字换到首位，分数沿用原来的排序位置。 */
    fun fix(ranked: List<Pair<String, Float>>, strokes: List<List<Pair<Float, Float>>>): List<Pair<String, Float>> {
        val top = ranked.firstOrNull()?.first ?: return ranked
        val d = DAKUTEN.indexOf(top)
        val h = HANDAKUTEN.indexOf(top)
        if (d < 0 && h < 0) return ranked
        val circle = endsWithCircle(strokes) ?: return ranked
        val partner = when {
            circle && d >= 0 -> HANDAKUTEN[d].toString()
            !circle && h >= 0 -> DAKUTEN[h].toString()
            else -> return ranked
        }
        val labels = listOf(partner) + ranked.map { it.first }.filter { it != partner }
        val scores = ranked.map { it.second }
        return labels.take(ranked.size).mapIndexed { i, label -> label to scores[i] }
    }

    private fun extent(strokes: List<List<Pair<Float, Float>>>): Float {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (s in strokes) for ((x, y) in s) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        return max(maxX - minX, maxY - minY)
    }
}
