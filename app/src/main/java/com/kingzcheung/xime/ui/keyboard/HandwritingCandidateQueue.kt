package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import kotlin.math.ln

/** Every candidate represents all unconfirmed writing; selection commits it in one operation. */
internal class HandwritingCandidateQueue {
    private val pending = ArrayDeque<List<HandwritingCandidate>>()
    private var phrases: List<String> = emptyList()
    val candidates: List<String> get() = phrases
    val size: Int get() = pending.size

    fun append(segments: List<Segment>) {
        segments.map { segment -> segment.candidates.filter { it.char.isNotBlank() }.distinctBy { it.char }.take(10) }
            .filter { it.isNotEmpty() }.forEach(pending::addLast)
        rebuild()
    }

    private fun rebuild() {
        if (pending.isEmpty()) { phrases = emptyList(); return }
        var beam = listOf("" to 0.0)
        for (segment in pending) {
            beam = beam.flatMap { (prefix, score) ->
                segment.map { candidate ->
                    val probability = candidate.score.takeIf { it.isFinite() }?.coerceIn(0.000001f, 1f) ?: 0.000001f
                    (prefix + candidate.char) to (score + ln(probability.toDouble()))
                }
            }.sortedByDescending { it.second }.distinctBy { it.first }.take(20)
        }
        phrases = beam.map { it.first }
    }

    fun select(index: Int): String? = candidates.getOrNull(index)?.also { clear() }
    fun deleteLast() { if (pending.isNotEmpty()) pending.removeLast(); rebuild() }
    fun clear() { pending.clear(); phrases = emptyList() }
    fun confirmAll(): String = candidates.firstOrNull().orEmpty().also { clear() }
}
