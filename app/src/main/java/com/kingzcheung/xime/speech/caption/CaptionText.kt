package com.kingzcheung.xime.speech.caption

private val richTags = Regex("<\\|[^|]*\\|>")
private val whitespace = Regex("[\\p{Z}\\s]+")

/**
 * Caption text keeps the model's punctuation: a reader needs sentence boundaries, and nothing
 * here is committed to an editor. Only the rich tags, no-speech results and SenseVoice's
 * CJK token spacing are removed (same rules as [com.kingzcheung.xime.speech.cleanSenseVoiceText]).
 */
internal fun cleanCaptionText(text: String, detectedLanguage: String = ""): String {
    if (detectedLanguage.removePrefix("<|").removeSuffix("|>").equals("nospeech", ignoreCase = true) ||
        text.contains("<|nospeech|>", ignoreCase = true)) return ""
    val plain = richTags.replace(text, "")
    fun cjk(c: Char) = c in '぀'..'ヿ' || c in '㐀'..'鿿' || c in '　'..'〿' || c in '＀'..'￯'
    val joined = buildString {
        var i = 0
        while (i < plain.length) {
            val c = plain[i]
            if (!c.isWhitespace()) { append(c); i++; continue }
            var next = i + 1
            while (next < plain.length && plain[next].isWhitespace()) next++
            val left = lastOrNull()
            val right = plain.getOrNull(next)
            val tokenSpace = left != null && right != null &&
                (cjk(left) && (cjk(right) || right.isDigit()) || left.isDigit() && cjk(right))
            if (!tokenSpace) append(' ')
            i = next
        }
    }
    return whitespace.replace(joined, " ").trim()
}

/** Speech event label without the <|…|> wrapper; "Speech" and unknown values become empty. */
internal fun captionEvent(rawEvent: String): String {
    val event = rawEvent.removePrefix("<|").removeSuffix("|>").trim()
    return if (event.isEmpty() || event.equals("Speech", ignoreCase = true)) "" else event
}
