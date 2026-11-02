package com.kingzcheung.xime.service

/** a–z typed straight into the editor; on a [com.kingzcheung.xime.settings.LetterLanguage] keyboard also its [extraLetters] (ñ, ä, é, й…). */
internal fun isDirectLetter(key: String, extraLetters: String): Boolean = key.length == 1 &&
    (key[0] in 'a'..'z' || key[0] in 'A'..'Z' || key[0] in extraLetters)

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
    require(key.length == 1 && key[0].isLetter())
    // ß 没有单个大写字母（uppercase 是 SS）：Shift 时照旧打 ß。
    val text = if (shifted) key.uppercase().takeIf { it.length == 1 } ?: key else key.lowercase()
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
