package com.kingzcheung.xime.service

/**
 * 打 @ 时给常用邮箱后缀（别人请求，B 站 11-02，默认关）：光标前是「邮箱名@」时候选栏给 qq.com、163.com 这些，
 * 接着打的字母（邮箱框里还有数字）只留开头对得上的；点一个补上没打完的那段，不加空格。
 * 只看光标前已有的文字，不联网，不学习。
 */
internal object EmailSuffixes {
    /** 设置键，默认关。 */
    const val PREF_KEY = com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences.EMAIL_SUFFIXES_KEY

    /** 读光标前多少个字：够放下邮箱名和后缀，也不少于联想上下文（25）。 */
    const val CONTEXT_LENGTH = 64

    /** 国内常用的在前。 */
    val DOMAINS = listOf(
        "qq.com", "163.com", "gmail.com", "126.com", "outlook.com",
        "foxmail.com", "icloud.com", "hotmail.com", "sina.com", "yahoo.com",
    )

    // 邮箱名（字母、数字和 . _ % + -）+ @ + 已经打了的一段后缀，都在光标前的最后。
    private val pattern = Regex("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]*)$")

    /** 光标前正在打邮箱 @ 后面那段时，已经打了的那段（可以是空串）；不是邮箱返回 null。 */
    fun typedDomain(before: CharSequence?): String? =
        before?.let { pattern.find(it)?.groupValues?.get(1) }

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

    /** 候选栏上现在是邮箱后缀（不是英文补全或中文联想）。 */
    fun isSuggestionList(words: List<String>): Boolean = words.isNotEmpty() && words.all { it in DOMAINS }
}
