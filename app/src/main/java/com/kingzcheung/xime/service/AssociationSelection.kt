package com.kingzcheung.xime.service

/**
 * No candidate state is changed until the editor accepts the selected text.
 * [suffix] follows an English completion (the auto space); it is never added to Chinese associations.
 */
internal fun commitAssociationSelection(
    state: CandidateState,
    index: Int,
    commit: (String) -> Boolean,
    replace: (String, String) -> Boolean,
    suffix: String = "",
): Boolean {
    val pending = state.pendingEnglishText
    val hasOriginal = pending.isNotEmpty() && state.englishReplaceSupported
    val word = associationWordAt(state, index) ?: return false
    return when {
        pending.isEmpty() -> commit(word)
        // The original word is already in the editor; only the suffix is new.
        hasOriginal && index == 0 -> suffix.isEmpty() || commit(suffix)
        !state.englishReplaceSupported -> false
        else -> replace(pending, word + suffix) // Stale cursor context must never append the whole word.
    }
}
