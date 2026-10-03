package com.kingzcheung.xime.service

/** No candidate state is changed until the editor accepts the selected text. */
internal fun commitAssociationSelection(
    state: CandidateState,
    index: Int,
    commit: (String) -> Boolean,
    replace: (String, String) -> Boolean,
): Boolean {
    val pending = state.pendingEnglishText
    val hasOriginal = pending.isNotEmpty() && state.englishReplaceSupported
    val words = if (hasOriginal) listOf(pending) + state.associationCandidates else state.associationCandidates
    val word = words.getOrNull(index) ?: return false
    return when {
        pending.isEmpty() -> commit(word)
        hasOriginal && index == 0 -> true // The original word is already in the editor.
        !state.englishReplaceSupported -> false
        else -> replace(pending, word) // Stale cursor context must never append the whole word.
    }
}
