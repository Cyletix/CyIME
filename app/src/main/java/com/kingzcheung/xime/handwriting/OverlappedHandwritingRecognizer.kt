package com.kingzcheung.xime.handwriting

/**
 * 叠写（连写）识别引擎：在单字识别模型之上实现多字连笔切分。
 *
 * 原理：过分割 + 动态规划组合打分。把笔画序列按所有可能的切分点切段，
 * 每段送单字模型识别，DP 求全局最优切分。段间比较用"段均分 + 切分惩罚"，
 * 解决部件成字问题（如"张" = 弓 + 长，两者都是独立汉字）：
 * 未写完时即使被切成 [弓][卜]，写完后合并段"张"的分数更高，均分比较
 * 会切回单段，上屏内容随之替换纠正。
 *
 * 缓存约定：笔画序列只允许尾部追加（前缀不变），段推理结果跨笔画复用；
 * 笔画缓冲被裁剪/清空时必须调用 [reset] 使缓存失效。
 *
 * 推理成本：每追加一笔，仅新增 ≤ [maxStrokesPerSegment] 次单字 ONNX 推理
 * （其余段全部命中缓存），后台线程调用即可。
 */
class OverlappedHandwritingRecognizer(
    /** 活动笔画最多同时叠写的字数，超过后由调用方固化最早的字。 */
    private val maxSegments: Int = DEFAULT_MAX_SEGMENTS,
    /** 单个字允许的最大笔画数（超出视为不可识别为单字）。 */
    private val maxStrokesPerSegment: Int = DEFAULT_MAX_STROKES_PER_SEGMENT,
    /**
     * 并排书写：新的一段整体落在前面所有笔迹右边、中间留出明显空隙时，当作换字（和停顿同等的切分信号）。
     * 日语、英文手写打开；中文习惯叠写在同一处，不打开（左右结构的字不会被拆开）。
     */
    private val sideBySide: Boolean = false,
    /**
     * 英文字母：一个字母的笔画在左右方向总是连着或重叠（i、j 的点在竖上方），
     * 所以左右之间出现明显空隙的一段一定是两个字母，重罚，不让 c、o 这样挨着写的两个字母合成 O。
     * 假名不能这样做（い、ハ 的两笔本身就左右分开）。
     */
    private val lettersApart: Boolean = false,
) {
    /**
     * 单个切分段（一个字）。
     *
     * @param startStroke 段起始笔画索引（0-based，相对本次 recognize 输入）。
     * @param strokeCount 段内笔画数。
     * @param candidates 候选字列表（降序，非空）。
     */
    data class Segment(
        val startStroke: Int,
        val strokeCount: Int,
        val candidates: List<HandwritingCandidate>,
        /** 公式模式：整段笔迹重新识别的结果，替换掉之前未上屏的候选，而不是接在后面。 */
        val replacesPending: Boolean = false,
    )

    /**
     * 识别结果。
     *
     * @param segments 最优切分（时间序）：最后一段为"当前正在写的字"，
     *                 前面的段已可固化上屏。段候选非空。
     * @param totalScore 最优切分的识别分总和。
     * @param avgScore 段均分（切分比较依据）。
     */
    data class Result(
        val segments: List<Segment>,
        val totalScore: Float,
        val avgScore: Float,
    )

    private val segCache = HashMap<Long, List<HandwritingCandidate>>()

    /**
     * 对笔画序列做叠写识别。
     *
     * @param strokes 笔画序列（时间序，前缀不可变；超长时自动裁剪并清缓存）。
     * @param gaps 笔间时间间隔（毫秒）：gaps[j] = 第 j 笔起笔与第 j-1 笔收笔的
     *             间隔（gaps[0] 恒为 0）。分割信号：停顿处倾向切分、连笔处压制切分。
     *             与 strokes 长度一致或为空（视为无间隔信息）。
     * @param predictFn 单字识别函数，生产环境用 [HandwritingEngine.predict]；
     *                  测试注入假模型。
     */
    fun recognize(
        strokes: List<List<Pair<Float, Float>>>,
        gaps: List<Long> = emptyList(),
        predictFn: (List<List<Pair<Float, Float>>>, Int) -> List<HandwritingCandidate> =
            { s, k -> HandwritingEngine.predict(s, k) }
    ): Result {
        val bounded = if (strokes.size > maxSegments * maxStrokesPerSegment) {
            reset()
            strokes.takeLast(maxSegments * maxStrokesPerSegment)
        } else {
            strokes
        }
        val n = bounded.size
        if (n == 0) return Result(emptyList(), 0f, 0f)
        val layout = if (sideBySide || lettersApart) SideBySide(bounded) else null

        val neg = Float.NEGATIVE_INFINITY
        // best[k][i]：前 i 笔切成 k 段的最大识别分总和（含时间间隔偏置）
        val best = Array(maxSegments + 1) { FloatArray(n + 1) { neg } }
        val back = Array(maxSegments + 1) { IntArray(n + 1) { -1 } }
        best[0][0] = 0f

        for (k in 1..maxSegments) {
            for (i in 1..n) {
                val minJ = (i - maxStrokesPerSegment).coerceAtLeast(0)
                for (j in minJ until i) {
                    val prev = best[k - 1][j]
                    if (prev == neg) continue
                    val candidates = segmentCandidates(j, i, bounded, predictFn)
                    if (candidates.isEmpty()) continue
                    // 时间偏置：j>0 时新段起笔与上一段收笔的间隔影响切分倾向
                    val gapBias = if (j > 0 && j < gaps.size) gapBias(gaps[j]) else 0f
                    // 位置偏置：并排书写时，这一段整体在前面笔迹右边并留出空隙，倾向在此换字
                    val spaceBias = if (j > 0 && layout?.isBoundary(j, i) == true) GAP_SPLIT_BONUS else 0f
                    val spanPenalty = if (lettersApart && layout?.spansGap(j, i) == true) LETTER_SPAN_PENALTY else 0f
                    // 假名、汉字大致方正：比行高窄很多的一段多半是半个字（い 的一笔被认成 し）
                    val narrowPenalty = if (!lettersApart) layout?.narrowPenalty(j, i) ?: 0f else 0f
                    // 从左往右写时，下一个字不会退回去压在前面的字上（に 的右半被认成 ト、し）；停顿后叠写不算
                    val pausedHere = j < gaps.size && gaps[j] >= GAP_SPLIT_MS
                    val overlapPenalty = if (j > 0 && !pausedHere) layout?.overlapPenalty(j, i) ?: 0f else 0f
                    val score = prev + candidates[0].score + gapBias + spaceBias - spanPenalty - narrowPenalty - overlapPenalty
                    if (best[k][i] == neg || score > best[k][i]) {
                        best[k][i] = score
                        back[k][i] = j
                    }
                }
            }
        }

        // 段间比较：段均分 - 每多切一段的惩罚。均分避免"两段低分字之和
        // 虚高压过单段高分字"；惩罚避免"多一刀白赚分数"。
        var bestK = 0
        var bestNorm = neg
        for (k in 1..maxSegments) {
            val total = best[k][n]
            if (total == neg) continue
            val norm = total / k - SEGMENT_PENALTY * (k - 1)
            if (norm > bestNorm) {
                bestNorm = norm
                bestK = k
            }
        }
        if (bestK == 0) return Result(emptyList(), 0f, 0f)

        // 最小诊断日志（2026-09-25 手写“第1/3字”排查）：把切分决策的输入与结论一次打出来
        android.util.Log.i(
            "HandwritingChain",
            "recognize strokes=$n gaps=$gaps bestK=$bestK norm=$bestNorm pauseBoundary=${hasPauseBoundary(gaps)}" +
                " sideBySide=${layout?.hasBoundary()}"
        )

        // "一个字"先验：DP 选出多段时，必须存在"显著换字停顿"才信任切分。
        // 显著性用相对阈值判定——边界间隔显著高于窗口内笔间间隔中位数
        // （慢写者字内间隔本身高，绝对阈值会把匀速书写的复杂字拆开；
        //   快写者字间间隔虽小但相对突出，仍能正确检出）。
        // 无显著停顿时按单字处理（合并段识别），输入框始终只显示一个字；
        // 连笔写多字词时合并段分数必然极低（跨字笔迹不成字），仍尊重 DP 切分。
        // 并排书写（日语、英文）不走这条：靠上面的位置判断（空隙、偏窄、压回前字）切分。
        if (bestK >= 2 && !hasPauseBoundary(gaps) && layout == null) {
            val merged = segmentCandidates(0, n, bounded, predictFn)
            val mergedScore = merged.firstOrNull()?.score ?: 0f
            var end = n
            var confidentFragments = true
            for (k in bestK downTo 1) {
                val start = back[k][end]
                if ((segmentCandidates(start, end, bounded, predictFn).firstOrNull()?.score ?: 0f) < MERGED_CHAR_MIN_SCORE) {
                    confidentFragments = false
                }
                end = start
            }
            // Weak Han fragments must not multiply an uncertain single character into several.
            // Keep clear multi-character results and explicit inter-character pauses intact.
            if (merged.isNotEmpty() && (mergedScore >= MERGED_CHAR_MIN_SCORE ||
                    n <= maxStrokesPerSegment && !confidentFragments)) {
                android.util.Log.i(
                    "HandwritingChain",
                    "recognize→merged single char score=$mergedScore bestK=$bestK strokes=$n"
                )
                return Result(listOf(Segment(0, n, merged)), mergedScore, mergedScore)
            }
        }

        val segments = mutableListOf<Segment>()
        var i = n
        for (k in bestK downTo 1) {
            val j = back[k][i]
            segments.add(0, Segment(j, i - j, segmentCandidates(j, i, bounded, predictFn)))
            i = j
        }
        val total = best[bestK][n]
        android.util.Log.i(
            "HandwritingChain",
            "recognize→segments=${segments.size} counts=${segments.map { it.strokeCount }} " +
                "heads=${segments.map { it.candidates.firstOrNull()?.char }}"
        )
        return Result(segments, total, total / bestK)
    }

    /** 笔画缓冲被裁剪或清空时调用，使段缓存失效。 */
    fun reset() {
        segCache.clear()
    }

    /**
     * 窗口滑窗：头部裁剪 k 笔后调用。段缓存 key 平移（(j,i)→(j-k,i-k)），
     * 被裁笔画之外的段全部复用，无需重新推理。
     */
    fun onStrokesTrimmed(k: Int) {
        if (k <= 0) return
        val shifted = HashMap<Long, List<HandwritingCandidate>>()
        for ((key, candidates) in segCache) {
            val j = (key / 1000L).toInt()
            val i = (key % 1000L).toInt()
            if (i > k) shifted[(j - k).toLong() * 1000L + (i - k)] = candidates
        }
        segCache.clear()
        segCache.putAll(shifted)
    }

    private fun segmentCandidates(
        j: Int,
        i: Int,
        strokes: List<List<Pair<Float, Float>>>,
        predictFn: (List<List<Pair<Float, Float>>>, Int) -> List<HandwritingCandidate>
    ): List<HandwritingCandidate> {
        val key = j.toLong() * 1000L + i
        segCache[key]?.let { return it }
        val segment = strokes.subList(j, i).map { stroke ->
            stroke.map { Pair(it.first, it.second) }
        }
        val candidates = predictFn(segment, SEGMENT_TOP_K).take(SEGMENT_TOP_K)
        segCache[key] = candidates
        return candidates
    }

    /**
     * 切分点时间偏置：笔间停顿是分字的最强信号——
     * 明显停顿（≥500ms）倾向在此切分（加分）；
     * 连笔（≤150ms）压制切分（减分），避免单字中途被拆出部件字。
     */
    private fun gapBias(gapMs: Long): Float = when {
        gapMs >= GAP_SPLIT_MS -> GAP_SPLIT_BONUS
        gapMs <= GAP_JOIN_MS -> GAP_JOIN_MALUS
        else -> 0f
    }

    /**
     * 显著换字停顿检测（相对阈值）：
     * - 窗口内笔间间隔样本少（<3 个）时用绝对阈值 [GAP_SPLIT_MS]（无中位数参照）；
     * - 否则边界间隔 ≥ max(中位数×2, [GAP_PAUSE_MIN_MS]) 才算换字。
     * 匀速慢写的复杂字（字内间隔均匀偏高）无显著边界 → 判定单字；
     * 快写多字词（字间间隔相对突出）→ 正确检出边界。
     */
    private fun hasPauseBoundary(gaps: List<Long>): Boolean {
        val inner = gaps.drop(1)
        if (inner.isEmpty()) return false
        val threshold = if (inner.size < 3) {
            GAP_SPLIT_MS
        } else {
            val median = inner.sorted()[inner.size / 2]
            maxOf(median * 2, GAP_PAUSE_MIN_MS)
        }
        return inner.any { it >= threshold }
    }

    /**
     * 并排书写的位置判断：第 j 笔起的一段（到第 i 笔前）最左边，比前面所有笔迹最右边还靠右，
     * 且空隙不小于整行高度的 [SIDE_GAP_RATIO]，就算换字。字内部件之间的空隙通常更小，不会触发。
     */
    private class SideBySide(strokes: List<List<Pair<Float, Float>>>) {
        private val minX = FloatArray(strokes.size) { s -> strokes[s].minOfOrNull { it.first } ?: Float.MAX_VALUE }
        private val maxX = FloatArray(strokes.size) { s -> strokes[s].maxOfOrNull { it.first } ?: -Float.MAX_VALUE }
        private val gapNeeded: Float = strokes.flatten().let { pts ->
            if (pts.isEmpty()) Float.MAX_VALUE
            else (pts.maxOf { it.second } - pts.minOf { it.second }).coerceAtLeast(1f) * SIDE_GAP_RATIO
        }
        private val prefixMaxX = FloatArray(strokes.size + 1).also { a ->
            a[0] = -Float.MAX_VALUE
            for (s in strokes.indices) a[s + 1] = maxOf(a[s], maxX[s])
        }

        private val lineHeight = gapNeeded / SIDE_GAP_RATIO
        private val letterGap = lineHeight * LETTER_GAP_RATIO

        /** 这一段没往右推进，或最左边退回到前面笔迹最右边以内超过 [OVERLAP_FREE_RATIO] 行高时，退得越多罚得越多（最多 [OVERLAP_PENALTY]）。 */
        fun overlapPenalty(j: Int, i: Int): Float {
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            for (s in j until i) { left = minOf(left, minX[s]); right = maxOf(right, maxX[s]) }
            // 整段都没越过前面笔迹的右边（i 上的点、字里补的一笔），不是下一个字
            if (right - prefixMaxX[j] < ADVANCE_MIN_RATIO * lineHeight) return OVERLAP_PENALTY
            val back = (prefixMaxX[j] - left) / lineHeight - OVERLAP_FREE_RATIO
            return if (back <= 0f) 0f else OVERLAP_PENALTY * (back / OVERLAP_FULL_RATIO).coerceAtMost(1f)
        }

        /** 这一段的宽度不到行高的 [NARROW_RATIO] 时，越窄罚得越多（最多 [NARROW_PENALTY]）。 */
        fun narrowPenalty(j: Int, i: Int): Float {
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            for (s in j until i) { left = minOf(left, minX[s]); right = maxOf(right, maxX[s]) }
            val ratio = (right - left) / lineHeight
            return if (ratio >= NARROW_RATIO) 0f else NARROW_PENALTY * (1f - ratio.coerceAtLeast(0f) / NARROW_RATIO)
        }

        /** 第 j 到 i-1 笔在左右方向上有没有断开（按横向范围排序后，相邻两块之间空隙够大）。 */
        fun spansGap(j: Int, i: Int): Boolean {
            if (i - j < 2) return false
            val order = (j until i).sortedBy { minX[it] }
            var right = maxX[order[0]]
            for (s in order.drop(1)) {
                if (minX[s] - right >= letterGap) return true
                right = maxOf(right, maxX[s])
            }
            return false
        }

        fun isBoundary(j: Int, i: Int): Boolean {
            var left = Float.MAX_VALUE
            for (s in j until i) left = minOf(left, minX[s])
            return left - prefixMaxX[j] >= gapNeeded
        }

        /** 整段笔迹里有没有一处是并排换字（从那一笔起到最后都在前面笔迹右边）。 */
        fun hasBoundary(): Boolean = (1 until minX.size).any { isBoundary(it, minX.size) }
    }

    companion object {
        /** 并排书写时，字与字之间的空隙至少为整行高度的这个比例才算换字。 */
        const val SIDE_GAP_RATIO = 0.4f

        /** 假名、汉字：一段的宽度不到行高的这个比例就算偏窄（小写的っゃ约 0.4，几乎不罚）。 */
        const val NARROW_RATIO = 0.4f

        /** 偏窄的最大罚分（宽度趋近 0 时）。 */
        const val NARROW_PENALTY = 0.3f

        /** 后一个字压回前面笔迹里不超过行高的这个比例不罚（前一个字的收笔常伸到下一个字下面）。 */
        const val OVERLAP_FREE_RATIO = 0.15f

        /** 压回超出免罚部分达到行高的这个比例时罚满。 */
        const val OVERLAP_FULL_RATIO = 0.3f

        /** 后一个字至少要越过前面笔迹右边行高的这个比例。 */
        const val ADVANCE_MIN_RATIO = 0.1f

        /** 压回前面笔迹的最大罚分。 */
        const val OVERLAP_PENALTY = 0.3f

        /** 英文字母之间：空隙到整行高度的这个比例就算两个字母。 */
        const val LETTER_GAP_RATIO = 0.08f

        /** 一段英文笔迹左右断开时的罚分：大到几乎不会被选，但仍保证总有切法（字母数超过上限时）。 */
        const val LETTER_SPAN_PENALTY = 1f

        /**
         * 活动笔画最多同时叠写的字数。
         * 5 段上限保证单字书写中途的临时多段切分（如"在"写到 3 笔时的
         * [一][丿][丨]）不会被误当作多字而触发固化——写完后合并段分数
         * 更高会自动切回单段替换。
         */
        const val DEFAULT_MAX_SEGMENTS = 5
        const val DEFAULT_MAX_STROKES_PER_SEGMENT = 20
        private const val SEGMENT_PENALTY = 0.02f
        private const val SEGMENT_TOP_K = 8

        /** 笔间停顿达到此值视为"换字"切分点（加分）。 */
        const val GAP_SPLIT_MS = 500L
        /** 笔间间隔小于此值视为连笔（压制切分）。 */
        const val GAP_JOIN_MS = 150L
        private const val GAP_SPLIT_BONUS = 0.15f
        private const val GAP_JOIN_MALUS = -0.08f

        /**
         * 合并段（全窗口单字识别）分数达到此值视为"笔迹整体像一个字"：
         * 无显著停顿时强制按单字处理（上屏 1 个字），防止单字中途被拆成多字上屏。
         */
        private const val MERGED_CHAR_MIN_SCORE = 0.35f

        /** 相对停顿检测的绝对下限（ms）：中位数极低（极快连写）时防止阈值过低。 */
        private const val GAP_PAUSE_MIN_MS = 250L
    }
}
