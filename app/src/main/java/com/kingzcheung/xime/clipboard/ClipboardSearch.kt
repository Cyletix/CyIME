package com.kingzcheung.xime.clipboard

/**
 * Search keys for clipboard history. Each record stores the initials of its first
 * [KEY_SOURCE_CHARS] characters: a Han character becomes the first letter of its pinyin,
 * Latin letters and digits stay (lowercase), everything else becomes a space. Typing "dz"
 * then finds 「地址」; typing a plain text fragment matches the text itself.
 */
internal object ClipboardSearch {
    const val KEY_SOURCE_CHARS = 2000
    /** Stored for records whose text yields no key, so the backfill never revisits them. */
    const val EMPTY_KEY = " "
    const val RESULT_LIMIT = 200

    fun key(text: String, initialOf: (Char) -> Char?): String {
        val out = StringBuilder()
        for (c in text.take(KEY_SOURCE_CHARS)) {
            when {
                c in 'a'..'z' || c in '0'..'9' -> out.append(c)
                c in 'A'..'Z' -> out.append(c.lowercaseChar())
                isHan(c) -> out.append(initialOf(c)?.lowercaseChar()?.takeIf { it in 'a'..'z' } ?: ' ')
                else -> out.append(' ')
            }
        }
        return out.toString().replace(Regex(" +"), " ").trim().ifEmpty { EMPTY_KEY }
    }

    /**
     * Pinyin initials of a composition still being typed in the search field, so the results
     * follow the keys before a candidate is picked. The engine's preedit separates syllables
     * ("di'zhi", "d z"); each syllable gives its first letter. A run without vowels is a string of
     * abbreviations ("dz"), one initial per letter, with zh/ch/sh as one. Han characters already
     * picked inside the composition give their own initial.
     */
    fun composingInitials(preedit: String, initialOf: (Char) -> Char?): String {
        val out = StringBuilder()
        val run = StringBuilder()
        fun flush() {
            val s = run.toString().lowercase()
            run.clear()
            if (s.isEmpty()) return
            if (s.any { it in "aeiouv" }) { out.append(s[0]); return }
            var i = 0
            while (i < s.length) {
                out.append(s[i])
                i += if (s[i] in "zcs" && s.getOrNull(i + 1) == 'h') 2 else 1
            }
        }
        for (c in preedit) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' -> run.append(c)
                isHan(c) -> { flush(); initialOf(c)?.lowercaseChar()?.takeIf { it in 'a'..'z' }?.let { out.append(it) } }
                else -> flush()
            }
        }
        flush()
        return out.toString()
    }

    /** Letters to the digit keys of a phone keypad; digits stay, everything else is dropped. */
    fun t9Digits(letters: String): String = buildString {
        for (c in letters.lowercase()) {
            when (c) {
                in '0'..'9' -> append(c)
                in 'a'..'z' -> append(T9_KEYS[c - 'a'])
            }
        }
    }

    /** The keypad digit of each letter a..z. */
    const val T9_KEYS = "22233344455566677778889999"

    /** A LIKE pattern for search keys: [key] anywhere (keys hold only letters, digits and spaces). */
    fun keyPattern(key: String): String = "%$key%"

    /** A LIKE pattern matching [query] anywhere; `%`, `_` and `\` in the query are literal. */
    fun likePattern(query: String): String =
        "%" + query.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun isHan(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'
}

/** Pinyin initials from Android's ICU (Han-Latin); cached per character. */
internal object AndroidPinyinInitials {
    private val cache = HashMap<Char, Char?>()
    private val transliterator by lazy {
        runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII") }.getOrNull()
    }

    @Synchronized
    fun initialOf(c: Char): Char? = cache.getOrPut(c) {
        transliterator?.transliterate(c.toString())?.firstOrNull { it.isLetter() }?.lowercaseChar()
    }
}

/**
 * What the keyboard search field holds: committed [text], and [composing], the preedit of a word
 * still being typed (empty when nothing is composing). On the 9-key keypad the composition is
 * matched by its keys, so every spelling of the same digits finds its records.
 */
internal data class ClipboardLiveQuery(val text: String, val composing: String = "", val t9: Boolean = false) {
    val isBlank get() = text.isBlank() && composing.isBlank()
    /** What the no-match line shows. */
    val display get() = (text + composing).trim()
}
