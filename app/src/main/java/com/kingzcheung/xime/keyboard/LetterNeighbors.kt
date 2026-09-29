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
