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

    /** 生僻字模板分数打的折扣：模板分 = 这个系数 × 相似度²（与模型概率同量纲）。 */
    const val RARE_WEIGHT = 0.5f
    /** 与模板的相似度低于这个值的生僻字不出。 */
    const val RARE_MIN_SIMILARITY = 0.6f
    /** 一次最多加几个生僻字。 */
    const val RARE_MAX = 4
    /** 没把握的生僻字排在模型前几个候选之后。 */
    const val RARE_AFTER = 3

    /**
     * 把字表外生僻字（[HanziRareTemplates]）并进候选，常用字的首选和前 [RARE_AFTER] 个不让位：
     * - 生僻字的模板分（[RARE_WEIGHT] × 相似度² × [mass]）和模型候选一起按常用度重排后仍排第一的，放最前面；
     * - 其余生僻字排在模型前 [RARE_AFTER] 个候选之后。
     * 分数仍按模型候选原来的分数依次分给新顺序（同 [preferCommon]），叠写切分只看首位分数，不受影响。
     *
     * @param han 模型给的汉字候选，概率从高到低（[preferCommon] 之前）
     * @param rare 生僻字和相似度，从高到低
     * @param mass 模型前若干类概率之和（模板相似度在归一化空间里，换回模型概率的量纲）
     */
    fun mergeRare(
        han: List<HandwritingCandidate>,
        rare: List<Pair<String, Float>>,
        weights: Map<String, Int>,
        mass: Float = 1f,
    ): List<HandwritingCandidate> {
        val ranked = preferCommon(han, weights)
        if (ranked.isEmpty()) return ranked
        val known = han.mapTo(HashSet()) { it.char }
        val add = rare.asSequence().filter { it.second >= RARE_MIN_SIMILARITY && it.first !in known }
            .distinctBy { it.first }.take(RARE_MAX)
            .map { HandwritingCandidate(it.first, RARE_WEIGHT * it.second * it.second * mass) }.toList()
        if (add.isEmpty()) return ranked
        val added = add.mapTo(HashSet()) { it.char }
        val strong = preferCommon((han + add).sortedByDescending { it.score }, weights)
            .takeWhile { it.char in added }.map { it.char }
        val weak = add.map { it.char }.filter { it !in strong }
        val order = (strong + ranked.take(RARE_AFTER).map { it.char } + weak + ranked.drop(RARE_AFTER).map { it.char }).distinct()
        return order.mapIndexed { i, c -> HandwritingCandidate(c, ranked.getOrNull(i)?.score ?: ranked.last().score) }
    }
}
