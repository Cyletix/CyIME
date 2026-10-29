package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class KanaMarksTest {
    /** は／ハ 一类的主体：几笔大笔画，占满 100×100。 */
    private val body = listOf(
        listOf(10f to 10f, 12f to 95f),
        listOf(30f to 30f, 80f to 30f),
        listOf(55f to 10f, 55f to 90f, 40f to 80f),
    )
    private fun circle(cx: Float, cy: Float, r: Float) =
        (0..12).map { i -> val a = i / 12.0 * 2 * Math.PI; (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat() }
    private val ticks = listOf(listOf(85f to 2f, 88f to 12f), listOf(93f to 0f, 96f to 10f))
    private val bo = listOf("ぼ" to 0.9f, "ぽ" to 0.8f, "ほ" to 0.5f)
    private val po = listOf("ぽ" to 0.9f, "ぼ" to 0.8f, "ほ" to 0.5f)

    @Test fun circleTurnsDakutenTopIntoHandakuten() {
        val strokes = body + listOf(circle(90f, 8f, 6f))
        assertEquals(listOf("ぽ" to 0.9f, "ぼ" to 0.8f, "ほ" to 0.5f), KanaMarks.fix(bo, strokes))
    }

    @Test fun ticksTurnHandakutenTopIntoDakuten() {
        assertEquals(listOf("ぼ" to 0.9f, "ぽ" to 0.8f, "ほ" to 0.5f), KanaMarks.fix(po, body + ticks))
    }

    @Test fun matchingMarkKeepsOrder() {
        assertEquals(bo, KanaMarks.fix(bo, body + ticks))
        assertEquals(po, KanaMarks.fix(po, body + listOf(circle(90f, 8f, 6f))))
    }

    @Test fun partnerMissingFromListIsInsertedWithTopScore() {
        val ranked = listOf("バ" to 0.7f, "ハ" to 0.6f)
        assertEquals(listOf("パ" to 0.7f, "バ" to 0.6f), KanaMarks.fix(ranked, body + listOf(circle(90f, 8f, 6f))))
    }

    @Test fun otherCharactersAndLargeLastStrokeAreLeftAlone() {
        val ka = listOf("か" to 0.9f, "が" to 0.8f)
        assertEquals(ka, KanaMarks.fix(ka, body + listOf(circle(90f, 8f, 6f))))
        // 最后一笔很大（主体的一部分），不当记号
        assertEquals(bo, KanaMarks.fix(bo, body + listOf(circle(50f, 50f, 40f))))
        assertEquals(bo, KanaMarks.fix(bo, listOf(circle(50f, 50f, 40f))))
    }
}
