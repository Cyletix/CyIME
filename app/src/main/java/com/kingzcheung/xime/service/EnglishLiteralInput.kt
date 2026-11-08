package com.kingzcheung.xime.service

import android.content.Context
import android.text.TextUtils
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences

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

internal const val ENGLISH_SENTENCE_CAPS = "english_sentence_caps"

/** Ask the editor about the real insertion point, including text the user moved into or selected. */
internal fun englishSentenceStart(context: Context, connection: InputConnection?): Boolean {
    if (!SettingsPreferences.getPrefsPublic(context).getBoolean(ENGLISH_SENTENCE_CAPS, false)) return false
    val sentences = TextUtils.CAP_MODE_SENTENCES
    return try {
        // TextUtils returns CAP_MODE_WORDS at the start of a paragraph even when only
        // sentences were requested. Requesting both would also capitalize ordinary words.
        val mode = connection?.getCursorCapsMode(sentences) ?: 0
        mode and (sentences or TextUtils.CAP_MODE_WORDS) != 0
    } catch (_: RuntimeException) {
        false // Some editors close their connection while switching fields.
    }
}
