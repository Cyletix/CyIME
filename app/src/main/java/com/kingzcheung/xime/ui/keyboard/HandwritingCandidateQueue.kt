package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment

/** Unconfirmed characters stay here, never in the host editor. Selection proceeds in writing order. */
internal class HandwritingCandidateQueue {
    private val pending = ArrayDeque<List<String>>()
    val candidates: List<String> get() = pending.firstOrNull().orEmpty()
    val size: Int get() = pending.size

    fun append(segments: List<Segment>) {
        val accepted = segments.map { it.candidates.map { candidate -> candidate.char }.filter(String::isNotBlank).distinct() }
            .filter { it.isNotEmpty() }
        accepted.forEach(pending::addLast)
        // 最小诊断日志：入队段数就是候选栏“第1/N字”的 N（N≠用户写的字数时要看识别器日志）
        android.util.Log.i(
            "HandwritingChain",
            "queue.append segments=${segments.size} accepted=${accepted.size} heads=${accepted.map { it.firstOrNull() }} size=$size"
        )
    }

    fun select(index: Int): String? = candidates.getOrNull(index)?.also { pending.removeFirst() }
    fun deleteLast() { if (pending.isNotEmpty()) pending.removeLast() }
    fun clear() = pending.clear()
    fun confirmAll(): String = pending.joinToString("") { it.first() }.also { clear() }
}
