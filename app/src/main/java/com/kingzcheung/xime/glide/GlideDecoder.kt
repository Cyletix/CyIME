package com.kingzcheung.xime.glide

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 英文滑行识别：把手指轨迹与词表中每个词“经过各字母键中心”的折线比较。
 *
 * 坐标统一除以键宽 [GlideKeyboard.keyUnit]，轨迹与模板都按弧长重采样到 [SAMPLES] 点，
 * 先用起点、终点距离筛掉绝大多数词，再对剩余词做带约束的 DTW；
 * 形状代价加上起止点偏差和词频先验（按 english.txt 的频率排名）排序。
 * 相邻重复字母合并（hello 与 helo 轨迹相同），同一轨迹的词按词频区分。
 * 源自 tools/glide_lab 的离线基线，只返回候选，不提交文字。
 */
class GlideKeyboard(val keyUnit: Float, val centers: Map<Char, Pair<Float, Float>>) {
    init {
        require(keyUnit.isFinite() && keyUnit > 0f)
    }

    /** 同一布局（键位置按 1/8 键宽量化）复用模板。 */
    val revision: Int = centers.entries.sortedBy { it.key }.fold(17) { hash, (key, point) ->
        31 * (31 * (31 * hash + key.code) + (point.first / keyUnit * 8f).toInt()) + (point.second / keyUnit * 8f).toInt()
    }
}

data class GlideCandidate(val word: String, val cost: Float)

class GlideDecoder(private val keyboard: GlideKeyboard, words: List<String>) {
    private class Template(val word: String, val prior: Float, val xs: FloatArray, val ys: FloatArray)

    private val templates: List<Template>
    val revision: Int get() = keyboard.revision

    init {
        val count = words.size.coerceAtLeast(1)
        val logMax = ln(count + 1.0).toFloat()
        templates = words.withIndex().mapNotNull { (rank, word) ->
            if (word.length !in 1..MAX_WORD || !word.all { keyboard.centers.containsKey(it) }) return@mapNotNull null
            val code = collapseRepeats(word)
            val px = FloatArray(code.length) { keyboard.centers.getValue(code[it]).first / keyboard.keyUnit }
            val py = FloatArray(code.length) { keyboard.centers.getValue(code[it]).second / keyboard.keyUnit }
            val (xs, ys) = resample(px, py)
            Template(word, ln(rank + 2.0).toFloat() / logMax, xs, ys)
        }
    }

    val size: Int get() = templates.size

    /** [xs]/[ys] 为与键中心同一坐标系的轨迹点；轨迹过短或没有足够接近的词时返回空表。 */
    fun decode(xs: FloatArray, ys: FloatArray, limit: Int = 6): List<GlideCandidate> {
        if (xs.size < 2 || xs.size != ys.size) return emptyList()
        val unit = keyboard.keyUnit
        val qx = FloatArray(xs.size) { xs[it] / unit }
        val qy = FloatArray(ys.size) { ys[it] / unit }
        if (pathLength(qx, qy) < MIN_PATH_KEYS) return emptyList()
        val (sx, sy) = resample(qx, qy)
        val last = SAMPLES - 1
        val results = ArrayList<GlideCandidate>()
        val shapes = HashMap<String, Float>()
        for (template in templates) {
            val start = hypot(sx[0] - template.xs[0], sy[0] - template.ys[0])
            if (start > MAX_ENDPOINT) continue
            val end = hypot(sx[last] - template.xs[last], sy[last] - template.ys[last])
            if (end > MAX_ENDPOINT) continue
            val shape = shapes.getOrPut(collapseRepeats(template.word)) { dtw(sx, sy, template.xs, template.ys) }
            if (shape > MAX_SHAPE) continue
            results += GlideCandidate(template.word,
                shape + (start + end) / 2f * ENDPOINT_WEIGHT + template.prior * PRIOR_WEIGHT)
        }
        return results.sortedWith(compareBy<GlideCandidate> { it.cost }.thenBy { it.word }).take(limit)
    }

    companion object {
        const val SAMPLES = 32
        const val MAX_WORD = 20
        const val MIN_PATH_KEYS = 0.75f
        const val MAX_ENDPOINT = 1.0f
        const val MAX_SHAPE = 1.0f
        const val ENDPOINT_WEIGHT = 0.35f
        /** 常用词与罕见词之间最多差这么多形状代价（单位：键宽）。 */
        const val PRIOR_WEIGHT = 0.2f

        internal fun collapseRepeats(word: String): String = buildString {
            for (char in word) if (isEmpty() || last() != char) append(char)
        }

        private fun pathLength(xs: FloatArray, ys: FloatArray): Float {
            var length = 0f
            for (i in 1 until xs.size) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return length
        }

        /** 按弧长等距取样；停顿不增加样本。单点（单字母词）重复同一点。 */
        internal fun resample(xs: FloatArray, ys: FloatArray): Pair<FloatArray, FloatArray> {
            val outX = FloatArray(SAMPLES)
            val outY = FloatArray(SAMPLES)
            val cumulative = FloatArray(xs.size)
            for (i in 1 until xs.size) cumulative[i] = cumulative[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            val total = cumulative.last()
            if (total <= 0f) {
                outX.fill(xs[0]); outY.fill(ys[0])
                return outX to outY
            }
            var segment = 1
            for (sample in 0 until SAMPLES) {
                val target = total * sample / (SAMPLES - 1)
                while (segment < xs.lastIndex && cumulative[segment] < target) segment++
                val length = cumulative[segment] - cumulative[segment - 1]
                val fraction = if (length <= 0f) 0f else ((target - cumulative[segment - 1]) / length).coerceIn(0f, 1f)
                outX[sample] = xs[segment - 1] + fraction * (xs[segment] - xs[segment - 1])
                outY[sample] = ys[segment - 1] + fraction * (ys[segment] - ys[segment - 1])
            }
            return outX to outY
        }

        /** 首尾固定、带宽为四分之一的 DTW，按样本数归一。 */
        private fun dtw(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
            val n = SAMPLES
            val band = max(1, n / 4)
            var previous = FloatArray(n + 1) { Float.POSITIVE_INFINITY }
            var current = FloatArray(n + 1) { Float.POSITIVE_INFINITY }
            previous[0] = 0f
            for (i in 1..n) {
                current.fill(Float.POSITIVE_INFINITY)
                for (j in max(1, i - band)..min(n, i + band)) {
                    current[j] = hypot(ax[i - 1] - bx[j - 1], ay[i - 1] - by[j - 1]) +
                        min(previous[j - 1], min(previous[j], current[j - 1]))
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[n] / n
        }
    }
}
