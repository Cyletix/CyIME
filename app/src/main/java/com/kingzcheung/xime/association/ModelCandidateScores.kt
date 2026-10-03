package com.kingzcheung.xime.association

/** JNI returns logits, not percentages. Normalize the returned batch without crossing-zero inversion.
 * This is a distribution over the returned top-k, not a calibrated full-vocabulary probability. */
internal fun normalizeModelCandidates(candidates: List<AssociationCandidate>): List<AssociationCandidate> {
    val valid = candidates.filter { it.score.isFinite() && it.text.isNotBlank() }
    val maximum = valid.maxOfOrNull { it.score } ?: return emptyList()
    val weights = valid.map { kotlin.math.exp(it.score.toDouble() - maximum.toDouble()) }
    val total = weights.sum()
    return valid.mapIndexed { index, candidate -> candidate.copy(score = (weights[index] / total).toFloat()) }
}
