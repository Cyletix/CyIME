package com.kingzcheung.xime.clipboard

/** Local keyword-based extraction; never treats an arbitrary number as an SMS code. */
object VerificationCodeExtractor {
    private val keywords = Regex(
        "验证码|驗證碼|校验码|校驗碼|确认码|確認碼|动态码|動態碼|認証(?:コード|番号)|確認(?:コード|番号)|認証碼|確認码|ワンタイム(?:パスワード|コード)|セキュリティコード|" +
            "\\b(?:verification|confirmation|security|authentication|login|one[ -]?time)\\s+(?:code|password)\\b|\\bOTP\\b",
        RegexOption.IGNORE_CASE,
    )
    private val digits = Regex("(?<![0-9A-Za-z])([0-9]{4,8})(?![0-9A-Za-z])")
    private val sentenceBoundary = Regex("[。.!?！？;；]")

    fun isCode(text: String): Boolean = text.length in 4..8 && text.all { it in '0'..'9' }

    fun extract(source: String): String? {
        if (source.length > 16_384) return null
        val text = source.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
        val words = keywords.findAll(text).toList()
        if (words.isEmpty()) return null
        val scored = digits.findAll(text).mapNotNull { number ->
            val before = text.take(number.range.first).takeLast(12)
            val after = text.drop(number.range.last + 1).take(12)
            if (Regex("[0-9][-/.:]$").containsMatchIn(before) ||
                Regex("^[-/.:][0-9]|^\\s*(?:年|月|日|円|元|秒|分钟|分間|minutes?\\b)", RegexOption.IGNORE_CASE).containsMatchIn(after)
            ) return@mapNotNull null
            val distance = words.mapNotNull { word ->
                val gap = when {
                    number.range.last < word.range.first -> text.substring(number.range.last + 1, word.range.first)
                    word.range.last < number.range.first -> text.substring(word.range.last + 1, number.range.first)
                    else -> ""
                }
                if (gap.length > 48 || sentenceBoundary.containsMatchIn(gap) || digits.containsMatchIn(gap)) null
                else gap.length
            }.minOrNull() ?: return@mapNotNull null
            number.value to distance
        }.toList()
        val best = scored.minOfOrNull { it.second } ?: return null
        // Equally plausible distinct codes require a manual choice, never guess.
        return scored.filter { it.second == best }.map { it.first }.distinct().singleOrNull()
    }
}
