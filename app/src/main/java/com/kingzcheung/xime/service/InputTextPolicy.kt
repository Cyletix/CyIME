package com.kingzcheung.xime.service

internal fun canPredictAfter(text: String): Boolean = text.lastOrNull()?.isLetterOrDigit() == true

internal fun isLiteralPunctuation(key: String): Boolean = key.length == 1 && when (key[0].category) {
    CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION,
    CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION,
    CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL,
    CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL -> true
    else -> false
}
