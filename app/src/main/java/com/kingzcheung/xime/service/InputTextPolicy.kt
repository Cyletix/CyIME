package com.kingzcheung.xime.service

internal fun canPredictAfter(text: String): Boolean = text.lastOrNull()?.isLetter() == true

/**
 * 这次上屏的以两个以上汉字结尾（整句、词、语音）：最后一个字已是一个词的末尾，联想只接下一个词，
 * 不再把它补成以它开头的长词（「你在吃饭莫」后不出“莫名其妙”）。单个字上屏照旧，可以补成词（我→们）。
 */
internal fun endsWithWholeWord(committed: String): Boolean =
    committed.takeLastWhile { it in '一'..'鿿' }.length >= 2

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

/** Japanese 英数 letters in full width (Ａ–Ｚ, ａ–ｚ); romaji inside Japanese text stays ASCII. */
internal fun fullWidthLetters(text: String): String =
    text.map { c -> if (c in 'a'..'z' || c in 'A'..'Z') (c.code + 0xFEE0).toChar() else c }.joinToString("")

/** Only keyboard literals use this policy; pasted text, speech and candidate words stay intact. */
internal fun keyboardLiteralWidth(text: String, full: Boolean, japanese: Boolean, numberPanel: Boolean = false, preserveWidth: Boolean = false,
    chineseKeyboard: Boolean = false, before: CharSequence? = null): String {
    if (preserveWidth) return text
    // 中文屏幕键盘的符号都是固定字符，印什么上屏什么；只有空格两侧的逗号句号由键盘按全角/半角给出。
    if (!japanese && chineseKeyboard) return text
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

/** 拼音条里表示光标的竖线。 */
internal const val PREEDIT_CARET = '|'

/**
 * 在显示的拼音里插入光标竖线。[input] 是引擎原始编码，[caret] 是光标在其中的位置，
 * [confirmed] 之前是已选定的部分。只要显示末尾的字母与光标后的编码一一对应就标，
 * 对不上（如合并键位改写了显示）或光标在末尾时返回 null。
 */
internal fun markPreeditCaret(preedit: String, input: String, caret: Int, confirmed: Int): String? {
    if (caret < 0 || caret >= input.length || caret < confirmed || preedit.isEmpty()) return null
    val tail = input.drop(confirmed)
    fun separator(c: Char) = c == ' ' || c == '\''
    // 从末尾往前数出未选定编码对应的显示字符，确认它们就是这些字母。
    // Rime 只转换光标前的编码，光标后原样接在末尾；只核对这一段，双拼也能标出光标。
    var index = preedit.length
    var matched = 0
    val after = tail.length - (caret - confirmed)
    while (index > 0 && matched < after) {
        val c = preedit[index - 1]
        if (separator(c)) { index--; continue }
        if (!c.equals(tail[tail.length - 1 - matched], ignoreCase = true)) return null
        matched++
        index--
    }
    if (matched != after) return null
    var insertAt = index
    // 光标落在音节分隔处时，竖线放在分隔符之前，紧贴前一个字母。
    while (insertAt > 0 && separator(preedit[insertAt - 1])) insertAt--
    return preedit.substring(0, insertAt) + PREEDIT_CARET + preedit.substring(insertAt)
}
