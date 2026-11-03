package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import kotlin.math.ln

/** Every candidate represents all unconfirmed writing; selection commits it in one operation. */
internal class HandwritingCandidateQueue {
    private val pending = ArrayDeque<List<HandwritingCandidate>>()
    private var phrases: List<String> = emptyList()
    /** 边写边识别时正在写的那段笔迹的预览：已认好的字 + 这段的结果；停顿后的正式结果到了就撤掉。 */
    private var previewPhrases: List<String>? = null
    /** 点了预览候选：笔迹已上屏，停顿那次识别如果抢在清板前回来也不要。下一笔开始新字时恢复。 */
    private var stale = false
    val candidates: List<String> get() = previewPhrases ?: phrases
    val size: Int get() = pending.size

    private fun List<Segment>.options() =
        map { segment -> segment.candidates.filter { it.char.isNotBlank() }.distinctBy { it.char }.take(10) }.filter { it.isNotEmpty() }

    fun append(segments: List<Segment>) {
        if (stale) return
        previewPhrases = null
        segments.options().forEach(pending::addLast)
        rebuild()
    }

    /** 写的过程中显示的候选；[segments] 为空时撤掉预览，回到已认好的候选。不改动已认好的部分。 */
    fun preview(segments: List<Segment>) {
        if (stale) return
        val options = segments.options()
        previewPhrases = if (options.isEmpty()) null else phrasesOf(pending + options)
    }

    /** 新的一个字开始写。 */
    fun newInk() { stale = false }

    private fun rebuild() { phrases = phrasesOf(pending) }

    private fun phrasesOf(segments: List<List<HandwritingCandidate>>): List<String> {
        if (segments.isEmpty()) return emptyList()
        var beam = listOf("" to 0.0)
        for (segment in segments) {
            beam = beam.flatMap { (prefix, score) ->
                segment.map { candidate ->
                    val probability = candidate.score.takeIf { it.isFinite() }?.coerceIn(0.000001f, 1f) ?: 0.000001f
                    (prefix + candidate.char) to (score + ln(probability.toDouble()))
                }
            }.sortedByDescending { it.second }.distinctBy { it.first }.take(20)
        }
        return beam.map { it.first }
    }

    fun select(index: Int): String? = candidates.getOrNull(index)?.also { if (previewPhrases != null) stale = true; clear() }
    fun deleteLast() { previewPhrases = null; if (pending.isNotEmpty()) pending.removeLast(); rebuild() }
    fun clear() { pending.clear(); phrases = emptyList(); previewPhrases = null }
    fun confirmAll(): String = candidates.firstOrNull().orEmpty().also { clear() }
}
