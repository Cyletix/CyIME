package com.kingzcheung.xime.keyboard

import kotlin.math.hypot

/** Measured keycaps, not a QWERTY table: reordered layouts and split gaps matter. */
data class LetterKeyRect(val x: Float, val y: Float, val width: Float, val height: Float)

fun encodeLetterNeighbors(keys: Map<Char, LetterKeyRect>): String {
    if (keys.keys != ('a'..'z').toSet() || keys.values.any {
        !it.x.isFinite() || !it.y.isFinite() || !it.width.isFinite() || !it.height.isFinite() || it.width <= 0f || it.height <= 0f
    }) return ""
    return keys.toSortedMap().entries.joinToString(";") { (letter, a) ->
        val near = keys.entries.asSequence().filter { it.key != letter }.map { (other, b) ->
            val dx = ((b.x + b.width / 2) - (a.x + a.width / 2)) / ((a.width + b.width) / 2)
            val dy = ((b.y + b.height / 2) - (a.y + a.height / 2)) / ((a.height + b.height) / 2)
            other to hypot(dx, dy)
        }.filter { it.second in 0.01f..1.3f }.sortedWith(compareBy({ it.second }, { it.first })).take(6)
            .map { it.first }.joinToString("")
        "$letter:$near"
    }
}

/**
 * What the engine gets (Rime property cyime_neighbors): the layout with correction on, nothing with it off. With
 * [unreadableOnly] (双拼邻键纠错 off) the layout goes marked "~;", and librime's NearSearchCorrector then corrects only
 * where the typed letters start no spelling at all, so a slip into a pair that spells nothing still gives candidates
 * while every pair that reads stays as typed.
 */
fun letterNeighborsForEngine(encoded: String, correction: Boolean, unreadableOnly: Boolean): String = when {
    encoded.isEmpty() -> ""
    correction -> encoded
    unreadableOnly -> "~;$encoded"
    else -> ""
}

/**
 * 「邻键纠错」全拼、双拼共用（11-03）：关着时什么都不交。开着时全拼整串纠；双拼只纠拼不成字的两个键，
 * 「双拼邻键纠错」也开才整串纠。
 */
fun letterNeighborsForEngine(encoded: String, neighborCorrection: Boolean, doublePinyin: Boolean, doublePinyinCorrection: Boolean): String =
    letterNeighborsForEngine(encoded, correction = neighborCorrection && (!doublePinyin || doublePinyinCorrection),
        unreadableOnly = neighborCorrection && doublePinyin && !doublePinyinCorrection)
