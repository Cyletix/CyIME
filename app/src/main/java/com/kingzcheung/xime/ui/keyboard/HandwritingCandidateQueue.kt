package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment

/** Unconfirmed characters stay here, never in the host editor. Selection proceeds in writing order. */
internal class HandwritingCandidateQueue {
    private val pending = ArrayDeque<List<String>>()
    val candidates: List<String> get() = pending.firstOrNull().orEmpty()
    val size: Int get() = pending.size

    fun append(segments: List<Segment>) {
        segments.map { it.candidates.map { candidate -> candidate.char }.filter(String::isNotBlank).distinct() }
            .filter { it.isNotEmpty() }.forEach(pending::addLast)
    }

    fun select(index: Int): String? = candidates.getOrNull(index)?.also { pending.removeFirst() }
    fun deleteLast() { if (pending.isNotEmpty()) pending.removeLast() }
    fun clear() = pending.clear()
    fun confirmAll(): String = pending.joinToString("") { it.first() }.also { clear() }
}
