package com.kingzcheung.xime.rime

/**
 * 全拼读音 → 小鹤双拼的键（与 double_pinyin_flypy_14jian 的 speller 规则同序）。
 * 14 键合并编码反算不出打的是哪个字母（bu'gc），屏上编码和原样上屏都按首选读音换成小鹤双拼（ni'hc），
 * 和 26 键小鹤双拼回车上屏打的双拼字母一致（10-27）。
 */
internal fun flypyReading(reading: String): String =
    reading.trim().split(' ', '\'').filter { it.isNotEmpty() }.joinToString(" ") { flypySyllable(it) }

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
