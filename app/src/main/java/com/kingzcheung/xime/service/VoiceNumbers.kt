package com.kingzcheung.xime.service

import java.math.BigDecimal

/*
 * 「数字转阿拉伯数字」(别人提的需求，默认关)：语音结果里读出来的中文数字写成阿拉伯数字。
 * 只改识别出来的文字，不碰模型；SenseVoice 自带的 ITN 照旧，已经是阿拉伯数字的部分不动。
 *
 * 宁可少转也不错转：
 * - 单个字不转：一个、第一、一点、十分、两个、三心二意；
 * - 约数和读不通的串整串不转：三四百、五六个、一五一十、三七二十一；
 * - 两个数字连读不转(五六、九九)，三个起才逐位转：一九九八 → 1998，幺三八 → 138；AABB(七七八八)不转；
 * - 常见带数字的成语、固定说法整词不转(见 [protectedPhrases])。
 */

private val digitValues = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
    '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '幺' to 1)
private val sectionUnits = mapOf('十' to 10, '百' to 100, '千' to 1000)
private const val ZEROS = "零〇"
/** 逐位读的数字(年份、电话)：没有「两」，有「幺」。 */
private const val BARE_DIGITS = "零〇一二三四五六七八九幺"
/** 小数点后面只认逐位读的数字。 */
private const val FRACTION_DIGITS = "零〇一二三四五六七八九"
private const val RUN_CHARS = "零〇一二两三四五六七八九幺十百千万亿"

/** 规则会转、但写成中文才对的说法。 */
private val protectedPhrases = listOf(
    "五十步笑百步", "三十六计", "十八般武艺", "十八罗汉", "三十而立", "四十不惑", "五十知天命", "六十耳顺",
    "七十二变", "七十二行", "三百六十行", "十万八千里", "十万火急", "一千零一夜", "九九八十一", "八十一难",
    "一百零八将", "二十四孝", "二十四节气", "十二生肖", "弱水三千", "白发三千丈", "一点一滴", "一点一点",
    "三点一线", "两点一线", "二百五",
)

/** 只转中文里读出来的数字；没有中文数字字符时原样返回。 */
internal fun spokenNumbersToDigits(text: String): String {
    if (text.none { it in RUN_CHARS }) return text
    val guarded = BooleanArray(text.length)
    for (phrase in protectedPhrases) {
        var at = text.indexOf(phrase)
        while (at >= 0) {
            for (k in at until at + phrase.length) guarded[k] = true
            at = text.indexOf(phrase, at + phrase.length)
        }
    }
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        if (!guarded[i] && text.startsWith("百分之", i)) {
            val percent = percentAt(text, i + 3, guarded)
            if (percent != null) { out.append(percent.text).append('%'); i = percent.end; continue }
        }
        val end = runEnd(text, i, guarded)
        if (end == i) { out.append(text[i]); i++; continue }
        val number = convertRun(text, i, end, guarded)
        if (number != null) { out.append(number.text); i = number.end; continue }
        // 读不通的整串原样留下，不从中间截一段转(一五一十 不能变成 一五10)。
        out.append(text, i, end); i = end
    }
    return out.toString()
}

private class Converted(val text: String, val end: Int)

private fun runEnd(text: String, start: Int, guarded: BooleanArray): Int {
    var e = start
    while (e < text.length && !guarded[e] && text[e] in RUN_CHARS) e++
    return e
}

/** 百分之五十 → 50%，百分之零点五 → 0.5%，百分之百 → 100%。 */
private fun percentAt(text: String, start: Int, guarded: BooleanArray): Converted? {
    val end = runEnd(text, start, guarded)
    if (end == start) return null
    val run = text.substring(start, end)
    if (run == "百") return Converted("100", end)
    if (end < text.length && text[end] == '点' && !guarded[end]) {
        decimalAt(text, start, end, guarded)?.let { return it }
    }
    val value = integerPart(run) ?: return null
    return Converted(value.toString(), end)
}

private fun convertRun(text: String, start: Int, end: Int, guarded: BooleanArray): Converted? {
    val run = text.substring(start, end)
    if (end < text.length && text[end] == '点' && !guarded[end]) {
        decimalAt(text, start, end, guarded)?.let { return it }
        timeAt(text, start, end, guarded)?.let { return it }
    }
    if (run.all { it in BARE_DIGITS }) {
        // 两个连读多是约数(五六个)或叠字(九九)；AABB 是叠词(七七八八)。
        if (run.length < 3) return null
        if (run.length == 4 && run[0] == run[1] && run[2] == run[3] && run[0] != run[2]) return null
        return Converted(run.map { digitValues.getValue(it) }.joinToString(""), end)
    }
    if (run.length < 2) return null
    val value = parseInteger(run) ?: return null
    return Converted(format(value, run), end)
}

/** 三点五 → 3.5，零点零五 → 0.05，二十点五 → 20.5；三点零五分 → 3点05分(时间)。 */
private fun decimalAt(text: String, start: Int, end: Int, guarded: BooleanArray): Converted? {
    val fractionStart = end + 1
    val fractionEnd = runEnd(text, fractionStart, guarded)
    if (fractionEnd == fractionStart) return null
    val fraction = text.substring(fractionStart, fractionEnd)
    if (!fraction.all { it in FRACTION_DIGITS }) return null
    val whole = integerPart(text.substring(start, end)) ?: return null
    val next = text.getOrNull(fractionEnd)
    if (next == '刻') return null // 三点一刻 是时间
    val digits = fraction.map { digitValues.getValue(it) }.joinToString("")
    if (next == '分' && fraction.length == 2 && fraction[0] in ZEROS && whole <= 24) {
        return Converted("${whole}点$digits", fractionEnd)
    }
    return Converted("$whole.$digits", fractionEnd)
}

/** 三点十五 → 3点15，十二点三十分 → 12点30分，一点半 → 1点半，十点钟 → 10点钟。 */
private fun timeAt(text: String, start: Int, end: Int, guarded: BooleanArray): Converted? {
    val hour = integerPart(text.substring(start, end))?.takeIf { it <= 24 } ?: return null
    val minuteStart = end + 1
    if (minuteStart < text.length && !guarded[minuteStart] && text[minuteStart] in "半钟整") {
        return Converted("${hour}点", minuteStart)
    }
    val minuteEnd = runEnd(text, minuteStart, guarded)
    val minuteRun = text.substring(minuteStart, minuteEnd)
    if (minuteRun.length < 2 || minuteRun.none { it in sectionUnits }) return null
    val minute = parseInteger(minuteRun)?.takeIf { it <= 59 } ?: return null
    return Converted("${hour}点$minute", minuteEnd)
}

/** 小数、时间的整数部分：单字也算(零点五、两点半、十点钟)，但「幺」不算。 */
private fun integerPart(run: String): Long? =
    if (run.length == 1 && run[0] != '幺' && run[0] in digitValues) digitValues.getValue(run[0]).toLong() else parseInteger(run)

/** 带单位的整数：三百二十五、两千零二十六、一万五(=15000)、一亿五千万。逐位读或读不通返回 null。 */
internal fun parseInteger(run: String): Long? {
    if (run.isEmpty() || run.none { it in "十百千万亿" } || '幺' in run) return null
    // 「两」只在百、千、万、亿前面是数(两百、两万)；十两、两个的「两」是量词。
    run.forEachIndexed { index, c -> if (c == '两' && run.getOrNull(index + 1)?.let { it in "百千万亿" } != true) return null }
    val yi = run.indexOf('亿')
    if (yi >= 0) {
        if (run.indexOf('亿', yi + 1) >= 0) return null
        val high = parseWan(run.substring(0, yi)) ?: return null
        val low = parseTail(run.substring(yi + 1), 10_000_000L, ::parseWan) ?: return null
        return high * 100_000_000L + low
    }
    return parseWan(run)
}

private fun parseWan(run: String): Long? {
    if (run.isEmpty() || '亿' in run) return null
    val wan = run.indexOf('万')
    if (wan < 0) return parseSection(run)
    if (run.indexOf('万', wan + 1) >= 0) return null
    val high = parseSection(run.substring(0, wan)) ?: return null
    val low = parseTail(run.substring(wan + 1), 1000L, ::parseSection) ?: return null
    return high * 10_000L + low
}

/** 万、亿后面的部分：空 = 0；单个数字是口语省略(一万五 = 15000)；不带零时必须占满最高位(一万三百 读不通)。 */
private fun parseTail(rest: String, place: Long, parse: (String) -> Long?): Long? {
    if (rest.isEmpty()) return 0
    if (rest.length == 1 && rest[0] !in ZEROS) return digitValues[rest[0]]?.let { it * place }
    val zero = rest[0] in ZEROS
    val body = rest.trimStart('零', '〇')
    if (body.isEmpty()) return null
    val value = parse(body) ?: return null
    if (value >= place * 10 || !zero && value < place) return null
    return value
}

/** 一万以内：数字和十、百、千交替，零只在单位后面，单位从大到小；末尾省略的位按口语算(三百五 = 350)。 */
private fun parseSection(run: String): Long? {
    if (run.isEmpty()) return null
    var total = 0L
    var digit = -1
    var lastUnit = 10_000
    var zero = false
    run.forEachIndexed { index, c ->
        val unit = sectionUnits[c]
        when {
            c in ZEROS -> { if (index == 0 || digit >= 0 || zero) return null; zero = true }
            unit != null -> {
                if (unit >= lastUnit) return null
                // 跳位要有零：一千零十 可以，一千十 读不通。
                if (lastUnit != 10_000 && unit * 10 != lastUnit && !zero) return null
                val d = when {
                    digit >= 0 -> digit
                    unit == 10 && (index == 0 || zero) -> 1
                    else -> return null
                }
                total += d.toLong() * unit; lastUnit = unit; digit = -1; zero = false
            }
            else -> { if (digit >= 0) return null; digit = digitValues[c] ?: return null }
        }
    }
    if (digit >= 0) {
        total += if (lastUnit == 10_000 || lastUnit == 10 || zero) digit.toLong() else digit.toLong() * lastUnit / 10
    } else if (zero) return null
    return total
}

/** 整万、整亿留单位：三万 → 3万，一千五百万 → 1500万，一亿五千万 → 1.5亿；其他写全(一万五 → 15000)。 */
private fun format(value: Long, run: String): String = when {
    '亿' in run && value >= 100_000_000L && value % 10_000L == 0L ->
        BigDecimal(value).divide(BigDecimal(100_000_000L)).stripTrailingZeros().toPlainString() + "亿"
    '万' in run && value >= 10_000L && value % 10_000L == 0L -> "${value / 10_000L}万"
    else -> value.toString()
}
