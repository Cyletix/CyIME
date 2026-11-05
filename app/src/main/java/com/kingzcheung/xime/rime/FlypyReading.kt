package com.kingzcheung.xime.rime

/**
 * 全拼读音 → 小鹤双拼的键（与 double_pinyin_flypy_14jian 的 speller 规则同序）。
 * 14 键合并编码反算不出打的是哪个字母（bu'gc），屏上编码和原样上屏都按首选读音换成小鹤双拼（ni'hc），
 * 和 26 键小鹤双拼回车上屏打的双拼字母一致（10-27）。
 */
internal fun flypyReading(reading: String): String =
    reading.trim().split(' ', '\'').filter { it.isNotEmpty() }.joinToString(" ") { flypySyllable(it) }

/**
 * 同上，按 [schemaId] 自己的双拼键位换（自然码、自定义双拼的 14 键版本）；小鹤双拼和查不到键位表时用小鹤。
 * 键位表里没有的音节原样保留。
 */
internal fun doublePinyinReading(reading: String, schemaId: String): String {
    val scheme = com.kingzcheung.xime.settings.DoublePinyinSchemes.find(schemaId)
    if (scheme == null || scheme.id == com.kingzcheung.xime.settings.DoublePinyinScheme.FLYPY.id) return flypyReading(reading)
    return reading.trim().split(' ', '\'').filter { it.isNotEmpty() }.joinToString(" ") { syllable ->
        scheme.code(syllable.lowercase().replace('ü', 'v')) ?: syllable
    }
}

private val FLYPY_RULES = listOf(
    "^([aoe])(ng)?$" to "$1$1$2", "iu$" to "Ⓠ", "(.)ei$" to "$1Ⓦ", "uan$" to "Ⓡ", "[uv]e$" to "Ⓣ", "un$" to "Ⓨ",
    "^sh" to "Ⓤ", "^ch" to "Ⓘ", "^zh" to "Ⓥ", "uo$" to "Ⓞ", "ie$" to "Ⓟ", "(.)i?ong$" to "$1Ⓢ", "ing$|uai$" to "Ⓚ",
    "(.)ai$" to "$1Ⓓ", "(.)en$" to "$1Ⓕ", "(.)eng$" to "$1Ⓖ", "[iu]ang$" to "Ⓛ", "(.)ang$" to "$1Ⓗ", "ian$" to "Ⓜ",
    "(.)an$" to "$1Ⓙ", "(.)ou$" to "$1Ⓩ", "[iu]a$" to "Ⓧ", "iao$" to "Ⓝ", "(.)ao$" to "$1Ⓒ", "ui$" to "Ⓥ", "in$" to "Ⓑ",
).map { (pattern, replacement) -> Regex(pattern) to replacement }
private const val CIRCLED = "ⓆⓌⓇⓉⓎⓊⒾⓄⓅⓈⒹⒻⒼⒽⒿⓀⓁⓏⓍⒸⓋⒷⓃⓂ"
private const val KEYS = "qwrtyuiopsdfghjklzxcvbnm"

internal fun flypySyllable(syllable: String): String {
    var code = syllable.lowercase().replace('ü', 'v')
    for ((pattern, replacement) in FLYPY_RULES) code = pattern.replace(code, replacement)
    return code.map { c -> CIRCLED.indexOf(c).let { if (it >= 0) KEYS[it] else c } }.joinToString("")
}

private const val T9_LETTERS = "abcdefghijklmnopqrstuvwxyz"
private const val T9_KEYS = "22233344455566677778889999"

/**
 * 双拼九键开着「编码显示双拼键」时的编码：按首选的读音 [spelling]（全拼，如 shuang pin）把 [raw] 里每个数字换成
 * [scheme] 的双拼字母，两键一字隔开（8572 → ul pb）；只按了一个键的字显示这个键的字母。
 * 读音不是这个方案能打的音节（英文词）、字母和按下的键对不上（含左栏选的全拼字母）、读音比按键短、
 * 分隔符落在一个字中间时为 null，照原来显示。
 */
internal fun t9DoublePinyinKeys(raw: String, spelling: String, scheme: com.kingzcheung.xime.settings.DoublePinyinScheme): String? {
    val syllables = spelling.trim().split(' ', '\'').filter { it.isNotEmpty() }
    if (syllables.isEmpty()) return null
    val codes = syllables.map { scheme.code(it.lowercase().replace('ü', 'v')) }
    if (codes.any { it == null }) return null
    val letters = codes.joinToString("")
    val out = StringBuilder()
    var used = 0
    for (c in raw) {
        if (c == '\'' || c == ' ') {
            if (used % 2 != 0) return null
            continue
        }
        val letter = letters.getOrNull(used) ?: return null
        val matches = if (c.isDigit()) T9_LETTERS.indexOf(letter).let { it >= 0 && T9_KEYS[it] == c } else c.lowercaseChar() == letter
        if (!matches) return null
        if (used > 0 && used % 2 == 0) out.append(' ')
        out.append(letter)
        used++
    }
    return out.toString().takeIf { it.isNotEmpty() }
}
