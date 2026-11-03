package com.kingzcheung.xime.service

/**
 * 打 @ 时给常用邮箱后缀（默认开）：光标前是「邮箱名@」时候选栏给 qq.com、163.com 这些，
 * 接着打的字母、数字和点只留开头对得上的（中文键盘下也原样上屏，不进拼音）；点一个补上没打完的那段，不加空格。
 * 中文符号面板打的全角 ＠ 也认，选后缀时换成半角 @。只看光标前已有的文字，不联网，不学习。
 */
internal object EmailSuffixes {
    /** 设置键，默认开。 */
    const val PREF_KEY = com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences.EMAIL_SUFFIXES_KEY

    /** 读光标前多少个字：够放下邮箱名和后缀，也不少于联想上下文（25）。 */
    const val CONTEXT_LENGTH = 64

    /** 国内常用的在前。 */
    val DOMAINS = listOf(
        "qq.com", "163.com", "gmail.com", "126.com", "outlook.com",
        "foxmail.com", "icloud.com", "hotmail.com", "sina.com", "yahoo.com",
    )

    // 邮箱名（字母、数字和 . _ % + -）+ @（中文符号面板给全角 ＠）+ 已经打了的一段后缀，都在光标前的最后。
    private val pattern = Regex("[A-Za-z0-9._%+-]+([@＠])([A-Za-z0-9.-]*)$")

    /** 中文键盘的句号键、全角点：给着后缀时接着打的点按半角 . 上屏。 */
    val DOTS = setOf(".", "。", "．")

    /** 光标前正在打邮箱 @ 后面那段时，已经打了的那段（可以是空串）；不是邮箱返回 null。 */
    fun typedDomain(before: CharSequence?): String? =
        before?.let { pattern.find(it)?.groupValues?.get(2) }

    /** 开头对得上、还没打完的后缀；[typed] 为空时全部给。 */
    fun matching(typed: String): List<String> =
        DOMAINS.filter { it.length > typed.length && it.startsWith(typed, ignoreCase = true) }

    /** 光标前 [before] 正在打邮箱时给的后缀；不是邮箱时为空。 */
    fun suggestions(before: CharSequence?): List<String> = typedDomain(before)?.let(::matching).orEmpty()

    /** 选了 [domain] 还要补上的那段；光标前已经对不上（挪了光标、改了字）返回 null。 */
    fun remainder(before: CharSequence?, domain: String): String? {
        val typed = typedDomain(before) ?: return null
        return domain.takeIf { it in matching(typed) }?.substring(typed.length)
    }

    /**
     * 选了 [domain] 时光标前要换掉的那段和换成的字：「@ 已经打的」→「@ 整个后缀」（全角 ＠ 一起换成半角）。
     * 光标前已经对不上返回 null。
     */
    fun completion(before: CharSequence?, domain: String): Pair<String, String>? {
        val match = before?.let { pattern.find(it) } ?: return null
        val typed = match.groupValues[2]
        if (domain !in matching(typed)) return null
        return match.groupValues[1] + typed to "@" + domain
    }

    /** 接着打 [next] 后还有对得上的后缀（[before] 是光标前的文字）。 */
    fun continues(before: CharSequence?, next: String): Boolean =
        typedDomain(before)?.let { matching(it + next).isNotEmpty() } == true

    /** 候选栏上现在是邮箱后缀（不是英文补全或中文联想）。 */
    fun isSuggestionList(words: List<String>): Boolean = words.isNotEmpty() && words.all { it in DOMAINS }
}
