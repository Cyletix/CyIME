package com.kingzcheung.xime.handwriting

import kotlin.math.ln
import kotlin.math.max

/** Chinese handwriting candidates, before segmentation; keep original confidence for scoring. */
internal object HandwritingCandidatePolicy {
    /**
     * 常用度的分量：排序依据 = ln(模型概率) + 这个系数 × ln(常用度权重)。形近时常用字排前面（日/曰、还/迩）。
     * 电脑上用个人样本和 Make Me a Hanzi 标准笔迹调过，见开发报告。
     */
    const val FREQUENCY_ALPHA = 0.3f
    /** 常用度表里没有或很低的字按这个权重算（约为模型字表中位数的六分之一），生僻字之间不再互相压。 */
    const val FREQUENCY_FLOOR = 20f
    /** 只有概率不低于首选这个比例的候选才按常用度重排：模型明显更确定的生僻字不会被换下去，最多退到第三。 */
    const val FREQUENCY_CLOSE_RATIO = 0.5f

    fun isHanCharacter(text: String): Boolean = text.isNotEmpty() &&
        text.codePointCount(0, text.length) == 1 && Character.isIdeographic(text.codePointAt(0))
    fun select(candidates: List<HandwritingCandidate>, limit: Int): List<HandwritingCandidate> =
        candidates.asSequence().filter { isHanCharacter(it.char) && it.score.isFinite() && it.score >= 0f }
            .distinctBy { it.char }.take(limit.coerceAtLeast(0)).toList()

    /**
     * 按常用度调整候选顺序。分数仍按原来的从高到低依次分给新顺序，所以叠写切分（只看首位分数）不受影响。
     * [weights] 为空时原样返回。
     */
    fun preferCommon(candidates: List<HandwritingCandidate>, weights: Map<String, Int>): List<HandwritingCandidate> {
        if (weights.isEmpty() || candidates.size < 2) return candidates
        val cut = candidates.maxOf { it.score } * FREQUENCY_CLOSE_RATIO
        val (close, rest) = candidates.partition { it.score >= cut }
        val order = close.sortedByDescending {
            ln(max(it.score, 1e-12f)) + FREQUENCY_ALPHA * ln(max(weights[it.char]?.toFloat() ?: 0f, FREQUENCY_FLOOR))
        } + rest
        return order.mapIndexed { i, c -> HandwritingCandidate(c.char, candidates[i].score) }
    }
}
