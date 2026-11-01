package com.kingzcheung.xime.glide

import kotlin.math.hypot
import kotlin.math.ln

/**
 * 九键滑行识别：把轨迹与「数字键串」比较，不逐字母比较。
 *
 * 九键只有 8 个数字键，一个键对应 3–4 个字母（64426 = ni hao / ni gan / mi han…），
 * 逐字母 DTW 在这个粒度上没有意义。所以：
 *  - 模板按「数字串」去重：同一数字串的形状只有一条，只算一次 DTW；
 *  - 组内多个拼音编码按词频先验排序，最终汉字交给 Rime；
 *  - 先用起手键／收手键剪枝（{@link byEnds}），再对剩下的做重采样 DTW。
 *
 * 离线实测（tools/glide_lab 合成轨迹，2 万条数字串）：
 * 不削角首选 76.8%、前三 95.3%；中等削角（0.15）首选 73.5%。
 * 真实手指轨迹尚未采集，合成轨迹偏乐观，不能当识别率。
 */
class T9GlideDecoder(private val keypad: GlideKeyboard, codes: List<String>) {

    private class Group(val digits: String, val xs: FloatArray, val ys: FloatArray) {
        val codes = ArrayList<String>()
        val priors = ArrayList<Float>()
    }

    private val groups: List<Group>
    private val byEnds: Map<Int, List<Group>>
    private val keys: CharArray

    val revision: Int get() = keypad.revision

    /** 不同数字串的数量（不是编码数量）。 */
    val size: Int get() = groups.size

    init {
        val count = codes.size.coerceAtLeast(1)
        val logMax = ln(count + 1.0).toFloat()
        val map = LinkedHashMap<String, Group>()
        for ((rank, code) in codes.withIndex()) {
            val digits = digitsOf(code) ?: continue
            if (digits.isEmpty() || digits.length > MAX_DIGITS) continue
            val collapsed = collapseRepeats(digits)
            if (!collapsed.all { keypad.centers.containsKey(it) }) continue
            val group = map.getOrPut(collapsed) {
                val centers = keypad.centers
                val px = FloatArray(collapsed.length) { centers.getValue(collapsed[it]).first / keypad.keyUnit }
                val py = FloatArray(collapsed.length) { centers.getValue(collapsed[it]).second / keypad.keyUnit }
                val (xs, ys) = resample(px, py)
                Group(collapsed, xs, ys)
            }
            group.codes += code
            group.priors += ln(rank + 2.0).toFloat() / logMax
        }
        groups = map.values.toList()
        keys = keypad.centers.keys.sorted().toCharArray()

        val index = HashMap<Char, Int>()
        for ((i, key) in keys.withIndex()) index[key] = i
        val buckets = HashMap<Int, MutableList<Group>>()
        for (group in groups) {
            if (group.digits.length < 2) continue
            val first = index[group.digits.first()] ?: continue
            val last = index[group.digits.last()] ?: continue
            buckets.getOrPut(first * 16 + last) { ArrayList() }.add(group)
        }
        byEnds = buckets
    }

    /**
     * [xs]/[ys] 为与键中心同一坐标系的轨迹点；轨迹过短或没有合理解时返回空表。
     * [limit] 是返回的拼音编码数，[perGroup] 是同一数字串最多取几个编码。
     */
    fun decode(xs: FloatArray, ys: FloatArray, limit: Int = 6, perGroup: Int = 2): List<GlideCandidate> {
        if (xs.size < 2 || xs.size != ys.size) return emptyList()
        val unit = keypad.keyUnit
        val tx = FloatArray(xs.size) { xs[it] / unit }
        val ty = FloatArray(ys.size) { ys[it] / unit }
        var length = 0f
        for (i in 1 until tx.size) length += hypot(tx[i] - tx[i - 1], ty[i] - ty[i - 1])
        if (length < MIN_PATH_KEYS) return emptyList()

        val start = nearestKey(tx.first(), ty.first())
        val end = nearestKey(tx.last(), ty.last())
        val candidates = byEnds[bucketOf(start, end)] ?: return emptyList()

        // A short straight stroke between two different keys means the two-key
        // T9 sequence itself (6 -> 4 is 64, not the folded path of gong=4664).
        // Without this guard, DTW can match repeated-key templates to a straight
        // segment because repeated points collapse to the same geometric path.
        val direct = directTwoKeyPath(tx, ty, start, end)
        val pathCandidates = if (direct != null) {
            candidates.filter { it.digits == direct }
        } else candidates
        if (pathCandidates.isEmpty()) return emptyList()

        val (sx, sy) = resample(tx, ty)
        val scored = ArrayList<Pair<Group, Float>>(candidates.size)
        for (group in pathCandidates) {
            val shape = dtw(sx, sy, group.xs, group.ys)
            if (shape > MAX_SHAPE) continue
            scored += group to shape
        }
        if (scored.isEmpty()) return emptyList()
        scored.sortBy { it.second }

        val best = scored.first().second
        val out = ArrayList<GlideCandidate>()
        for ((group, shape) in scored) {
            if (shape > best + SHAPE_SLACK) break
            val take = minOf(perGroup, group.codes.size)
            for (i in 0 until take) {
                out += GlideCandidate(group.codes[i], shape + group.priors[i] * PRIOR_WEIGHT, shape)
            }
        }
        return out.sortedWith(compareBy<GlideCandidate> { it.cost }.thenBy { it.word }).take(limit)
    }

    private fun bucketOf(a: Char, b: Char): Int {
        val i = keys.indexOf(a).coerceAtLeast(0)
        val j = keys.indexOf(b).coerceAtLeast(0)
        return i * 16 + j
    }

    /** Return the direct two-key sequence for a clean two-key stroke, otherwise null. */
    private fun directTwoKeyPath(xs: FloatArray, ys: FloatArray, start: Char, end: Char): String? {
        if (start == end) return null
        val startRaw = keypad.centers.getValue(start)
        val endRaw = keypad.centers.getValue(end)
        val startCenter = startRaw.first / keypad.keyUnit to startRaw.second / keypad.keyUnit
        val endCenter = endRaw.first / keypad.keyUnit to endRaw.second / keypad.keyUnit
        val dx = endCenter.first - startCenter.first
        val dy = endCenter.second - startCenter.second
        val span = hypot(dx, dy)
        if (span <= 0f) return null
        var maxDeviation = 0f
        var previousProjection = Float.NEGATIVE_INFINITY
        var maximumBacktrack = 0f
        var firstProjection = 0f
        var lastProjection = 0f
        for (i in xs.indices) {
            val px = xs[i] - startCenter.first
            val py = ys[i] - startCenter.second
            val projection = (px * dx + py * dy) / (span * span)
            if (projection < -0.35f || projection > 1.35f) return null
            if (i == 0) firstProjection = projection
            if (i > 0) maximumBacktrack = maxOf(maximumBacktrack, previousProjection - projection)
            previousProjection = projection
            lastProjection = projection
            val deviation = kotlin.math.abs(px * dy - py * dx) / span
            maxDeviation = maxOf(maxDeviation, deviation)
        }
        val reachesBothKeys = firstProjection <= 0.35f && lastProjection >= 0.65f
        return if (reachesBothKeys && maximumBacktrack <= DIRECT_MAX_BACKTRACK &&
            maxDeviation <= DIRECT_MAX_DEVIATION) "$start$end" else null
    }

    private fun nearestKey(x: Float, y: Float): Char {
        var best = keys[0]
        var bestDistance = Float.MAX_VALUE
        for (key in keys) {
            val raw = keypad.centers.getValue(key)
            val distance = hypot(x - raw.first / keypad.keyUnit, y - raw.second / keypad.keyUnit)
            if (distance < bestDistance) { bestDistance = distance; best = key }
        }
        return best
    }

    companion object {
        const val MAX_DIGITS = 24
        const val MIN_PATH_KEYS = 0.75f
        const val MAX_SHAPE = 1.0f
        const val SHAPE_SLACK = 0.12f
        /** Allow normal finger wobble while rejecting a deliberate fold-back. */
        const val DIRECT_MAX_DEVIATION = 0.42f
        const val DIRECT_MAX_BACKTRACK = 0.28f
        const val PRIOR_WEIGHT = 0.25f
        const val SAMPLES = 32

        /** 数字键 → 字母。九键不把 0、1 算进拼音。 */
        val DIGIT_LETTERS: Map<Char, String> = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
            '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
        )

        private val LETTER_DIGIT: Map<Char, Char> =
            DIGIT_LETTERS.flatMap { (digit, letters) -> letters.map { it to digit } }.toMap()

        /** 拼音编码 → 数字串；滑行是连续的，所以忽略音节空格。 */
        internal fun digitsOf(code: String): String? = buildString {
            for (char in code) {
                if (char == ' ') continue
                append(LETTER_DIGIT[char] ?: return null)
            }
        }

        internal fun collapseRepeats(s: String): String = buildString {
            for (char in s) if (isEmpty() || last() != char) append(char)
        }

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

        private fun dtw(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
            val n = SAMPLES
            val band = maxOf(1, n / 4)
            var previous = FloatArray(n + 1) { Float.POSITIVE_INFINITY }
            var current = FloatArray(n + 1) { Float.POSITIVE_INFINITY }
            previous[0] = 0f
            for (i in 1..n) {
                current.fill(Float.POSITIVE_INFINITY)
                for (j in maxOf(1, i - band)..minOf(n, i + band)) {
                    current[j] = hypot(ax[i - 1] - bx[j - 1], ay[i - 1] - by[j - 1]) +
                        minOf(previous[j - 1], previous[j], current[j - 1])
                }
                val swap = previous; previous = current; current = swap
            }
            return previous[n] / n
        }
    }
}
