package com.kingzcheung.xime.handwriting

/** Chinese handwriting candidates, before segmentation; keep original confidence for scoring. */
internal object HandwritingCandidatePolicy {
    fun isHanCharacter(text: String): Boolean = text.isNotEmpty() &&
        text.codePointCount(0, text.length) == 1 && Character.isIdeographic(text.codePointAt(0))

    fun select(candidates: List<HandwritingCandidate>, limit: Int): List<HandwritingCandidate> =
        candidates.asSequence().filter { isHanCharacter(it.char) && it.score.isFinite() && it.score >= 0f }
            .distinctBy { it.char }.take(limit.coerceAtLeast(0)).toList()
}
