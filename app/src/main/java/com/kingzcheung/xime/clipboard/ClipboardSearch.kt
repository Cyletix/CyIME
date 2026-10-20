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
