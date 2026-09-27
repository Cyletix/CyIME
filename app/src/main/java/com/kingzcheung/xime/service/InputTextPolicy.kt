package com.kingzcheung.xime.service

internal fun canPredictAfter(text: String): Boolean = text.lastOrNull()?.isLetter() == true

internal fun isLiteralPunctuation(key: String): Boolean = key.length == 1 && when (key[0].category) {
    CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION,
    CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION,
    CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL,
    CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL -> true
    else -> false
}

/** Keyboard punctuation only: never rewrite pasted prose, digits, emoji or speech results. */
internal fun punctuationWidth(text: String, full: Boolean, japanese: Boolean = false): String {
    if (text.isEmpty() || !text.all { isLiteralPunctuation(it.toString()) }) return text
    return buildString {
        for (c in text) {
            if (full) {
                append(when (c) {
                    ',' -> if (japanese) '、' else '，'; '.' -> '。'; '｡' -> '。'; '､' -> '、'
                    '｢' -> '「'; '｣' -> '」'; '･' -> '・'
                    in '!'..'~' -> (c.code + 0xFEE0).toChar()
                    else -> c
                })
            } else {
                append(when (c) {
                    '。', '｡' -> '.'; '、', '､' -> ','; '・' -> '･'
                    '“', '”', '「', '」', '『', '』', '｢', '｣' -> '"'
                    '‘', '’' -> '\''
                    '【', '〔' -> '['; '】', '〕' -> ']'
                    '《', '〈' -> '<'; '》', '〉' -> '>'
                    in '！'..'～' -> (c.code - 0xFEE0).toChar()
                    else -> c
                })
            }
        }
    }
}

/** Only keyboard literals use this policy; pasted text, speech and candidate words stay intact. */
internal fun keyboardLiteralWidth(text: String, full: Boolean, japanese: Boolean, numberPanel: Boolean = false, preserveWidth: Boolean = false): String {
    if (preserveWidth) return text
    if (!japanese) return if (numberPanel) text else punctuationWidth(text, full)
    // Numeric-panel punctuation is arithmetic (a decimal point is not a Japanese full stop).
    if (numberPanel) return text.map { c ->
        when {
            full && c == ' ' -> '\u3000'
            full && c in '!'..'~' -> (c.code + 0xFEE0).toChar()
            !full && c == '\u3000' -> ' '
            !full && c in '！'..'～' -> (c.code - 0xFEE0).toChar()
            else -> c
        }
    }.joinToString("")
    return punctuationWidth(text, full, japanese = true).map { c ->
        when {
            full && c == ' ' -> '\u3000'
            full && c in '0'..'9' -> (c.code + 0xFEE0).toChar()
            !full && c == '\u3000' -> ' '
            !full && c in '０'..'９' -> (c.code - 0xFEE0).toChar()
            else -> c
        }
    }.joinToString("")
}
