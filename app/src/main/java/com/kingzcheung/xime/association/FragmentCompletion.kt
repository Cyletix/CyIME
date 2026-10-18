package com.kingzcheung.xime.association

import kotlin.math.exp

/**
 * 联想模型逐 token 预测，词表外的词会被切成单字，出现“夸”“关”“预”这类半个词。
 * 对排在前面的单个汉字，再向后预测一步：下一个字明显占优（相对次选与本批分布都够高）时
 * 并成完整的词（夸→夸奖），最多补 [MAX_STEPS] 步，遇到标点、非汉字或分布分散即停。
 * 独立成词的单字（很、我、点）后面分布分散，不会被拼长。
 */
internal object FragmentCompletion {
    // 每次上屏都在联想进程里串行推理：最多补 2 个碎片、各补 1 个字，并受总时间预算约束，
    // 避免上屏后一串模型调用与下一句的九键解码抢 CPU。
    const val MAX_FRAGMENTS = 2
    const val MAX_STEPS = 1
    const val BUDGET_NANOS = 80_000_000L
    const val PEEK_TOP_K = 8
    /** 首选与次选 logit 差，e² ≈ 7.4 倍概率比。 */
    const val MIN_MARGIN = 2.0f
    /** 首选在本批 top-k 内的占比下限（本批归一化，偏乐观，故与差值同时要求）。 */
    const val MIN_SHARE = 0.6f

    suspend fun complete(
        context: String,
        candidates: List<AssociationCandidate>,
        nanoTime: () -> Long = System::nanoTime,
        predictNext: suspend (String, Int) -> List<AssociationCandidate>,
    ): List<AssociationCandidate> {
        if (candidates.isEmpty()) return candidates
        val deadline = nanoTime() + BUDGET_NANOS
        val order = candidates.withIndex().sortedByDescending { it.value.score }
        val targets = order.filter { isHanFragment(it.value.text) }.take(MAX_FRAGMENTS).map { it.index }.toSet()
        if (targets.isEmpty()) return candidates
        val completed = candidates.mapIndexed { index, candidate ->
            if (index !in targets || nanoTime() > deadline) candidate
            else candidate.copy(text = extend(context, candidate.text, deadline, nanoTime, predictNext))
        }
        // 补全后可能与已有候选重复：保留排在前面的那个。
        val seen = HashSet<String>()
        return completed.withIndex().sortedByDescending { it.value.score }
            .filter { seen.add(it.value.text) }.sortedBy { it.index }.map { it.value }
    }

    private suspend fun extend(
        context: String, fragment: String, deadline: Long, nanoTime: () -> Long,
        predictNext: suspend (String, Int) -> List<AssociationCandidate>,
    ): String {
        var text = fragment
        repeat(MAX_STEPS) {
            if (nanoTime() > deadline) return text
            val next = dominantNext(predictNext(context + text, PEEK_TOP_K)) ?: return text
            text += next
        }
        return text
    }

    internal fun dominantNext(peek: List<AssociationCandidate>): String? {
        val valid = peek.filter { it.score.isFinite() && it.text.isNotBlank() }.sortedByDescending { it.score }
        val first = valid.firstOrNull() ?: return null
        if (!first.text.all { isHan(it) }) return null
        val second = valid.getOrNull(1)
        if (second != null && first.score - second.score < MIN_MARGIN) return null
        val share = 1.0 / valid.sumOf { exp((it.score - first.score).toDouble()) }
        return first.text.takeIf { share >= MIN_SHARE }
    }

    internal fun isHanFragment(text: String): Boolean = text.codePointCount(0, text.length) == 1 && isHan(text.codePointAt(0))

    private fun isHan(char: Char): Boolean = isHan(char.code)
    private fun isHan(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
}
