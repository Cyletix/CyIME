package com.kingzcheung.xime.stats

/** 统计页上的数字写法和称号。纯 Kotlin，电脑上能测。 */
object TypingStatsText {
    /** 1234 → 1,234；12345 → 1.2 万；1.2 亿同理。 */
    fun count(n: Long): String = when {
        n >= 100_000_000 -> trim(n / 100_000_000.0) + " 亿"
        n >= 10_000 -> trim(n / 10_000.0) + " 万"
        else -> String.format(java.util.Locale.US, "%,d", n)
    }

    private fun trim(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /** 算字数：不算空白；emoji、生僻字按一个字算。 */
    fun countChars(text: String): Long {
        var n = 0L
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!Character.isWhitespace(cp)) n++
            i += Character.charCount(cp)
        }
        return n
    }

    fun percent(rate: Double): String = "${(rate * 100).toInt().coerceIn(0, 100)}%"

    data class Title(val min: Long, val badge: String, val name: String)

    /** 按累计字数给的称号，从低到高。 */
    val titles = listOf(
        Title(0, "🌱", "初来乍到"),
        Title(1_000, "✏️", "渐入佳境"),
        Title(10_000, "📝", "笔耕不辍"),
        Title(100_000, "📚", "下笔千言"),
        Title(500_000, "🏆", "著作等身"),
        Title(1_000_000, "👑", "百万字作者"),
    )

    fun titleFor(totalChars: Long): Title = titles.last { totalChars >= it.min }

    /** 下一个称号还差多少字；已经最高时返回 null。 */
    fun nextTitle(totalChars: Long): Pair<Title, Long>? =
        titles.firstOrNull { it.min > totalChars }?.let { it to (it.min - totalChars) }

    /** 一篇作文按 800 字算。 */
    const val ESSAY_CHARS = 800L

    /** 分享时一种输入方式至少打过这么多字才列出来（全都不够时照样列出，免得分享出一张空卡）。 */
    const val SHARE_MIN_CHARS = 100L
    const val SHARE_MAX_ROWS = 10

    /** 分享卡上列哪些输入方式：字多的在前。 */
    fun shareRows(stats: TypingStatsData): List<TypingStatsData.ComboStats> {
        val all = stats.comboList()
        return all.filter { it.chars >= SHARE_MIN_CHARS }.ifEmpty { all }.take(SHARE_MAX_ROWS)
    }

    /** 速度一格：「62 字/分」；连续打字不到半分钟、或语音（不算速度）时为 null。 */
    fun speedText(c: TypingStatsData.ComboStats): String? =
        c.speed()?.let { "$it ${TypingStatsCombo.speedUnit(c.id)}" }

    /** 速度下面的一行小字：码长、首选率、天数、字数。没有的项不写。 */
    fun comboDetail(c: TypingStatsData.ComboStats): String = listOfNotNull(
        c.keysPerChar()?.let { "码长 " + String.format(java.util.Locale.US, "%.1f", it) },
        c.firstPickRate()?.let { "首选 " + percent(it) },
        "使用 ${c.days} 天",
        "${count(c.chars)} 字",
    ).joinToString(" · ")

    /**
     * 分享出去的文字：只有内置名称和数字，不含打过的字、不含自建布局的名字、不含设备标识。
     * [device] 是「手机」或「平板」，[version] 是 App 版本。
     */
    fun shareText(stats: TypingStatsData, device: String, version: String): String = buildString {
        append("我的 CyIME 打字速度(").append(device).append(")\n")
        for (c in shareRows(stats)) {
            append(c.label).append("：")
            speedText(c)?.let { append(it).append("，") }
            append(comboDetail(c).replace(" · ", "，")).append('\n')
        }
        append("仅含统计数字，不含输入内容 · CyIME ").append(version)
    }
}
