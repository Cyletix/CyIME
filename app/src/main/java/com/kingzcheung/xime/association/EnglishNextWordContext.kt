package com.kingzcheung.xime.association

import java.util.Locale

/**
 * The trailing English word boundary of the editor text: what to predict after, and which two words
 * were just completed (for personal pair learning). Only whitespace ends a word here, so a next-word
 * list is never shown while the current word is still being typed.
 */
internal data class EnglishNextWordContext(
    /** The last completed word, unless the last token is an address (zhang@example.com). */
    val previous: String?,
    /** The last two completed words, when the text ends right after the second one. */
    val pair: Pair<String, String>?,
    /** The last token as written; may be an email/domain for the personal phrase memory. */
    val lastToken: String?,
    /** True when the last character is whitespace and the character before it ends a word. */
    val justCompleted: Boolean,
) {
    companion object {
        private const val MAX_LENGTH = 128
        private val WORD = Regex("[A-Za-z][A-Za-z']{0,39}")
        private val TOKEN = Regex("[A-Za-z0-9@][A-Za-z0-9@._%+&'\\-]{0,63}")

        val EMPTY = EnglishNextWordContext(null, null, null, false)

        fun parse(text: CharSequence?): EnglishNextWordContext {
            if (text.isNullOrEmpty()) return EMPTY
            val tail = text.takeLast(MAX_LENGTH).toString()
            val trimmed = tail.trimEnd()
            val justCompleted = tail.lastOrNull()?.isWhitespace() == true &&
                tail.dropLast(1).lastOrNull()?.let { it.isLetter() || it == '\'' } == true
            if (trimmed.isEmpty()) return EMPTY
            val token = TOKEN.findAll(trimmed).lastOrNull()?.value?.takeIf { trimmed.endsWith(it) }
            val words = WORD.findAll(trimmed).map { it.value }.toList()
            val last = words.lastOrNull()
            // An address (zhang@example.com, user_name01) is a personal phrase, not a word to predict from.
            val previous = last?.takeIf { token == null || token == last }
            val pair = if (previous != null && words.size >= 2 && justCompleted) words[words.size - 2] to words.last() else null
            return EnglishNextWordContext(previous, pair, token, justCompleted)
        }

        /** Lookup key for the bigram tables: lowercase, apostrophes dropped (you're → youre). */
        fun normalize(value: String?): String? {
            val text = value?.trim()?.lowercase(Locale.ROOT) ?: return null
            val key = text.filter { it in 'a'..'z' || it == '\'' }.replace("'", "")
            return key.takeIf { it.length in 1..40 }
        }
    }
}
