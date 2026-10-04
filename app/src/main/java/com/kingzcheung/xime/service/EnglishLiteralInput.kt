package com.kingzcheung.xime.service

/** English letters are literal editor input, never a request to the Chinese decoder. */
internal fun commitEnglishLetter(
    state: CandidateState,
    key: String,
    shifted: Boolean,
    replaceSupported: Boolean,
    commit: (String) -> Boolean,
): CandidateState? {
    require(key.length == 1 && (key[0] in 'a'..'z' || key[0] in 'A'..'Z'))
    val text = if (shifted) key.uppercase() else key.lowercase()
    if (!commit(text)) return null
    return state.copy(
        inputText = "", preeditText = "", isComposing = false,
        candidates = emptyList(), candidateComments = emptyList(), candidateActions = emptyList(),
        pendingEnglishText = state.pendingEnglishText + text,
        associationCandidates = emptyList(), englishReplaceSupported = replaceSupported,
        hasNextPage = false, hasPrevPage = false, isShowingRecentClipboard = false,
    )
}
