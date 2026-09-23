package com.kingzcheung.xime.ui.keyboard

import kotlin.math.abs

sealed interface JapaneseKanaAction {
    data class Input(val romaji: String) : JapaneseKanaAction
    data object Modify : JapaneseKanaAction
}

internal enum class KanaFlickDirection { TAP, LEFT, UP, RIGHT, DOWN }
internal data class KanaChoice(val label: String, val romaji: String)
internal data class KanaFlickKey(val choices: List<KanaChoice?>) {
    val center get() = choices[0]!!
    fun choice(direction: KanaFlickDirection): KanaChoice? = choices[direction.ordinal]
}

private fun kanaKey(labels: String, vararg romaji: String?) = KanaFlickKey(
    labels.mapIndexed { index, label -> romaji[index]?.let { KanaChoice(label.toString(), it) } }
)

/** 顺序为点按、左、上、右、下，使用 jaroomaji 词典中的标准编码。 */
internal val japaneseKanaKeys = listOf(
    kanaKey("あいうえお", "a", "i", "u", "e", "o"),
    kanaKey("かきくけこ", "ka", "ki", "ku", "ke", "ko"),
    kanaKey("さしすせそ", "sa", "si", "su", "se", "so"),
    kanaKey("たちつてと", "ta", "ti", "tu", "te", "to"),
    kanaKey("なにぬねの", "na", "ni", "nu", "ne", "no"),
    kanaKey("はひふへほ", "ha", "hi", "hu", "he", "ho"),
    kanaKey("まみむめも", "ma", "mi", "mu", "me", "mo"),
    kanaKey("や（ゆ）よ", "ya", "(", "yu", ")", "yo"),
    kanaKey("らりるれろ", "ra", "ri", "ru", "re", "ro"),
    kanaKey("わをんー　", "wa", "wo", "nn", "-", null),
    kanaKey("、。？！・", ",", ".", "?", "!", "/"),
)

internal val japaneseKanaRomaji = japaneseKanaKeys.flatMap { it.choices }.mapNotNull { it?.romaji }.toSet()

internal fun kanaFlickDirection(dx: Float, dy: Float, threshold: Float): KanaFlickDirection = when {
    maxOf(abs(dx), abs(dy)) < threshold -> KanaFlickDirection.TAP
    abs(dx) > abs(dy) -> if (dx < 0) KanaFlickDirection.LEFT else KanaFlickDirection.RIGHT
    dy < 0 -> KanaFlickDirection.UP
    else -> KanaFlickDirection.DOWN
}

internal val kanaModifierCycles = listOf(
    listOf("a", "xa"), listOf("i", "xi"), listOf("u", "vu", "xu"), listOf("e", "xe"), listOf("o", "xo"),
    listOf("ka", "ga", "xka"), listOf("ki", "gi"), listOf("ku", "gu"), listOf("ke", "ge", "xke"), listOf("ko", "go"),
    listOf("sa", "za"), listOf("si", "zi"), listOf("su", "zu"), listOf("se", "ze"), listOf("so", "zo"),
    listOf("ta", "da"), listOf("ti", "di"), listOf("tu", "du", "xtu"), listOf("te", "de"), listOf("to", "do"),
    listOf("ha", "ba", "pa"), listOf("hi", "bi", "pi"), listOf("hu", "bu", "pu"), listOf("he", "be", "pe"), listOf("ho", "bo", "po"),
    listOf("ya", "xya"), listOf("yu", "xyu"), listOf("yo", "xyo"), listOf("wa", "xwa"),
)
