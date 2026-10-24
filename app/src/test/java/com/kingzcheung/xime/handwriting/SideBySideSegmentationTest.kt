package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 并排书写（日语、英文）：没有停顿时，靠左右位置的空隙切字；空隙小（字内部件）或叠在一处（中文叠写）不切。
 * 假模型：一个字（同一列的笔画）识别成那一列的名字，跨列的段只给低分的“合字”。
 */
class SideBySideSegmentationTest {
    /** 第 c 列的一笔：竖线，宽 [width]，列与列之间相隔 [pitch]（整行高 100）。 */
    private fun column(c: Int, pitch: Float, width: Float = 20f) =
        listOf(c * pitch to 0f, c * pitch + width to 50f, c * pitch to 100f)

    private val noPauses = listOf(0L, 120L, 120L, 120L)

    private fun fake(pitch: Float): (List<List<Pair<Float, Float>>>, Int) -> List<HandwritingCandidate> = { seg, _ ->
        val cols = seg.map { st -> (st.first().first / pitch).toInt() }.toSet()
        if (cols.size == 1) listOf(HandwritingCandidate("abc"[cols.first()].toString(), .8f))
        else listOf(HandwritingCandidate("合", .5f))
    }

    @Test fun charactersWrittenSideBySideWithoutPausesAreSplitByTheGapBetweenThem() {
        val strokes = listOf(column(0, 60f), column(1, 60f), column(2, 60f)) // 空隙 40 = 0.4 行高
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, sideBySide = true).recognize(strokes, noPauses.take(3), fake(60f))
        assertEquals(listOf("a", "b", "c"), result.segments.map { it.candidates.first().char })
    }

    @Test fun withoutSideBySideTheSameInkStaysOneCharacterAsBefore() {
        val strokes = listOf(column(0, 60f), column(1, 60f), column(2, 60f))
        val result = OverlappedHandwritingRecognizer().recognize(strokes, noPauses.take(3), fake(60f))
        assertEquals(1, result.segments.size)
    }

    @Test fun partsOfOneCharacterCloseTogetherAreNotSplit() {
        // 部件之间只隔 10（0.1 行高），不到换字空隙：按一个字处理
        val strokes = listOf(column(0, 30f), column(1, 30f))
        val oneChar: (List<List<Pair<Float, Float>>>, Int) -> List<HandwritingCandidate> = { seg, _ ->
            listOf(HandwritingCandidate(if (seg.size == 2) "明" else "日", .8f))
        }
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, sideBySide = true).recognize(strokes, noPauses.take(2), oneChar)
        assertEquals(listOf("明"), result.segments.map { it.candidates.first().char })
    }

    @Test fun overlappedWritingInOnePlaceIsNotSplitByPosition() {
        val strokes = listOf(column(0, 60f), column(0, 60f), column(0, 60f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, sideBySide = true).recognize(strokes, noPauses.take(3)) { _, _ ->
            listOf(HandwritingCandidate("川", .8f))
        }
        assertEquals(1, result.segments.size)
    }
}
