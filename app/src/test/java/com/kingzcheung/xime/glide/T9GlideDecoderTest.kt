package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Regression tests for numeric-key semantics before geometric ranking. */
class T9GlideDecoderTest {
    private val unit = 60f
    private val keys = buildMap<Char, LetterKeyRect> {
        val rows = listOf("234", "567", "89")
        rows.forEachIndexed { row, digits ->
            digits.forEachIndexed { column, digit ->
                put(digit, LetterKeyRect(column * unit, row * unit, unit, unit))
            }
        }
    }

    private fun center(digit: Char): Pair<Float, Float> = keys.getValue(digit).let {
        (it.x + it.width / 2f) to (it.y + it.height / 2f)
    }

    private fun straightTrace(from: Char, to: Char): Pair<FloatArray, FloatArray> {
        val start = center(from)
        val end = center(to)
        val xs = FloatArray(25) { index -> start.first + (end.first - start.first) * index / 24f }
        val ys = FloatArray(25) { index -> start.second + (end.second - start.second) * index / 24f }
        return xs to ys
    }

    @Test fun straightSixToFourPrefersNiOverFoldedGongPath() {
        val decoder = T9GlideDecoder(GlideLexicon.keyboard(keys), listOf("ni", "gong"))
        val (xs, ys) = straightTrace('6', '4')

        assertEquals("ni", decoder.decode(xs, ys).first().word)
    }

    @Test fun straightFiveToTwoProducesThe52PinyinCandidates() {
        val decoder = T9GlideDecoder(GlideLexicon.keyboard(keys), listOf("la", "ka", "ja"))
        val (xs, ys) = straightTrace('5', '2')
        val result = decoder.decode(xs, ys, limit = 6, perGroup = 3).map { it.word }

        assertEquals(listOf("la", "ka", "ja"), result)
    }

    @Test fun bundledLexiconMapsStraightSixToFourToNi() {
        val codes = File("src/main/assets/glide_pinyin.txt").readLines()
            .filter { it.isNotBlank() }
            .map { it.trim() }
        val decoder = T9GlideDecoder(GlideLexicon.keyboard(keys), pinyinCodes(codes.map { it.split(' ') }, null))
        val (xs, ys) = straightTrace('6', '4')
        val result = decoder.decode(xs, ys)

        assertEquals("ni", result.first().word)
        assertEquals("64", T9GlideDecoder.digitsOf(result.first().word))
    }
}
