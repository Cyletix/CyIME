package com.kingzcheung.xime.service

/** English letters are literal editor input, never a request to the Chinese decoder. */
internal fun commitEnglishLetter(
    state: CandidateState,
    key: String,
    shifted: Boolean,
    replaceSupported: () -> Boolean,
    /** 光标紧跟刚滑行上屏的词：这个字母开始新词，前面补空格。 */
    afterGlide: Boolean = false,
    commit: (String) -> Boolean,
): CandidateState? {
    require(key.length == 1 && (key[0] in 'a'..'z' || key[0] in 'A'..'Z'))
    val text = if (shifted) key.uppercase() else key.lowercase()
    if (!commit(if (afterGlide) " $text" else text)) return null
    return state.copy(
        inputText = "", preeditText = "", isComposing = false,
        candidates = emptyList(), candidateComments = emptyList(), candidateActions = emptyList(),
        pendingEnglishText = (if (afterGlide) "" else state.pendingEnglishText) + text, glidedWord = "",
        // Deliver the letter before asking the host about optional completion support.
        associationCandidates = emptyList(), englishReplaceSupported = replaceSupported(),
        hasNextPage = false, hasPrevPage = false, isShowingRecentClipboard = false,
    )
}
