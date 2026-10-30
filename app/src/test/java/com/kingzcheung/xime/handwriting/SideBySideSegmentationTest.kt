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
    @Test fun twoLettersWithAClearGapAreNotMergedIntoOneLetter() {
        // c 和 o 隔 0.4 行高：字母内部很少断开这么宽，即使合起来像 O 也切成两个字母
        val strokes = listOf(column(0, 60f), column(1, 60f))
        val merged: (List<List<Pair<Float, Float>>>, Int) -> List<HandwritingCandidate> = { seg, _ ->
            listOf(if (seg.size == 2) HandwritingCandidate("O", .97f)
                else HandwritingCandidate("co"[(seg[0].first().first / 60f).toInt()].toString(), .8f))
        }
        val letters = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses.take(2), merged)
        assertEquals(listOf("c", "o"), letters.segments.map { it.candidates.first().char })
    }

    @Test fun hWhoseCrossbarDoesNotTouchTheStemsStaysOneLetter() {
        // 演示时写的 H：横离两竖各约 0.1、0.17 行高，不罚，认成 H 的分数够高就是一个字母
        val strokes = listOf(listOf(0f to 0f, 3f to 100f), listOf(11f to 55f, 40f to 58f), listOf(57f to 0f, 60f to 100f))
        val names = mapOf(listOf(0, 1, 2) to ("H" to .85f), listOf(0, 1) to ("t" to .7f), listOf(2) to ("l" to .9f),
            listOf(0) to ("l" to .9f), listOf(1, 2) to ("+" to .5f), listOf(1) to ("-" to .6f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses.take(3)) { seg, _ ->
                val (name, score) = names.getValue(seg.map { strokes.indexOf(it) })
                listOf(HandwritingCandidate(name, score))
            }
        assertEquals(listOf("H"), result.segments.map { it.candidates.first().char })
    }

    @Test fun lettersThatDifferOnlyInSizeFollowTheirHeightInTheLine() {
        // 「Ho」：o 只有 H 的三分之一高，认成 O 也改成 o；矮字母旁边一样高的 c 改成 C
        assertEquals("o", OverlappedHandwritingRecognizer.byCase(listOf(HandwritingCandidate("O", .9f), HandwritingCandidate("0", .8f)), upper = false).first().char)
        assertEquals("o", OverlappedHandwritingRecognizer.byCase(listOf(HandwritingCandidate("0", .9f)), upper = false).first().char)
        assertEquals("C", OverlappedHandwritingRecognizer.byCase(listOf(HandwritingCandidate("c", .9f)), upper = true).first().char)
        assertEquals("e", OverlappedHandwritingRecognizer.byCase(listOf(HandwritingCandidate("e", .9f)), upper = true).first().char)
        val strokes = listOf(listOf(0f to 0f, 0f to 100f), listOf(40f to 60f, 70f to 60f, 70f to 100f, 40f to 100f, 40f to 60f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses.take(2)) { seg, _ ->
                listOf(HandwritingCandidate(if (seg.size == 2) "?" else if (seg[0].size == 2) "l" else "O", if (seg.size == 2) .1f else .9f))
            }
        assertEquals(listOf("l", "o"), result.segments.map { it.candidates.first().char })
    }

    @Test fun dotAboveTheStemStaysOneLetter() {
        // i：竖和上面的点左右重叠，不算断开
        val strokes = listOf(listOf(10f to 40f, 10f to 100f), listOf(10f to 10f, 11f to 12f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses.take(2)) { seg, _ -> listOf(HandwritingCandidate(if (seg.size == 2) "i" else "l", .8f)) }
        assertEquals(listOf("i"), result.segments.map { it.candidates.first().char })
    }
    @Test fun kanaWhoseTwoStrokesStandApartStaysOneCharacter() {
        // い：两笔左右分开（空隙 0.36 行高），每一笔单看也像个假名（し、へ），但都太窄，整体才是一个字
        val strokes = listOf(listOf(0f to 0f, 5f to 100f), listOf(40f to 10f, 50f to 40f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, sideBySide = true).recognize(strokes, noPauses.take(2)) { seg, _ ->
            listOf(if (seg.size == 2) HandwritingCandidate("い", .9f) else HandwritingCandidate(if (seg[0].first().first < 20f) "し" else "へ", .95f))
        }
        assertEquals(listOf("い"), result.segments.map { it.candidates.first().char })
    }
    @Test fun kanaWithASeparateLeftStrokeIsNotSplitIntoFragments() {
        // に：左边一竖，右边两横；拆成「ト」「し」时后一段压回前面笔迹里，拆成「丨」「こ」时左边太窄
        val strokes = listOf(listOf(0f to 0f, 4f to 100f), listOf(25f to 20f, 75f to 18f), listOf(25f to 80f, 75f to 82f))
        val names = mapOf(listOf(0, 1, 2) to ("に" to .85f), listOf(0) to ("丨" to .95f), listOf(1, 2) to ("こ" to .95f),
            listOf(0, 1) to ("ト" to .9f), listOf(2) to ("し" to .9f), listOf(1) to ("一" to .9f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, sideBySide = true).recognize(strokes, noPauses.take(3)) { seg, _ ->
            val (name, score) = names.getValue(seg.map { strokes.indexOf(it) })
            listOf(HandwritingCandidate(name, score))
        }
        assertEquals(listOf("に"), result.segments.map { it.candidates.first().char })
    }
    @Test fun dotOfIIsNotTakenAsANextLetterEvenWhenItLooksLikeOne() {
        // h 后面写 i：竖和点单看都像「1」，但点没往右推进，不是下一个字母
        val strokes = listOf(listOf(0f to 0f, 0f to 100f), listOf(0f to 60f, 30f to 55f, 30f to 100f),
            listOf(60f to 40f, 60f to 100f), listOf(60f to 15f, 61f to 17f))
        val names = mapOf(listOf(0, 1) to ("h" to .9f), listOf(2, 3) to ("i" to .85f), listOf(2) to ("1" to .95f), listOf(3) to ("1" to .9f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses) { seg, _ ->
                val (name, score) = names[seg.map { strokes.indexOf(it) }] ?: ("?" to .1f)
                listOf(HandwritingCandidate(name, score))
            }
        assertEquals(listOf("h", "i"), result.segments.map { it.candidates.first().char })
    }

    @Test fun dotAboveAStemIsReadAsIEvenWhenTheModelSaysOne() {
        // 你写的 antique 里的 i：模型对「点 + 竖」给 1 0.78、i 0.64；点在竖正上方就是 i
        val strokes = listOf(listOf(0f to 30f, 1f to 37f), listOf(-1f to 59f, 1f to 80f, 3f to 100f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, noPauses.take(2)) { seg, _ ->
                if (seg.size == 2) listOf(HandwritingCandidate("1", .78f), HandwritingCandidate("i", .64f)) else listOf(HandwritingCandidate("1", .7f))
            }
        assertEquals(listOf("i"), result.segments.map { it.candidates.first().char })
    }

    @Test fun lettersWrittenQuicklyOneAfterAnotherAreStillSplit() {
        // t 和 i 之间只隔 141 ms：中文的连笔压制不用在字母上，否则 ti 合成一个 t
        val strokes = listOf(listOf(0f to 30f, 30f to 30f), listOf(10f to 0f, 15f to 100f), listOf(45f to 40f, 46f to 100f))
        val names = mapOf(listOf(0, 1) to ("t" to .87f), listOf(2) to ("i" to .9f), listOf(0, 1, 2) to ("t" to .84f),
            listOf(0) to ("-" to .3f), listOf(1) to ("l" to .5f), listOf(1, 2) to ("H" to .3f))
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, listOf(0L, 140L, 141L)) { seg, _ ->
                val (name, score) = names.getValue(seg.map { strokes.indexOf(it) })
                listOf(HandwritingCandidate(name, score))
            }
        assertEquals(listOf("t", "i"), result.segments.map { it.candidates.first().char })
    }

    @Test fun twoWellRecognizedLettersAreNotMergedIntoOneWorseLetter() {
        // dapple 的 pp：两个 p 各 0.9，合起来也勉强像 P（0.89）；按均分比较会合成一个，按字母成本不会
        val p1 = listOf(listOf(0f to 0f, 0f to 100f), listOf(0f to 0f, 20f to 20f, 0f to 40f))
        val p2 = p1.map { st -> st.map { (x, y) -> x + 28f to y } }
        val strokes = p1 + p2
        val result = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true)
            .recognize(strokes, listOf(0L, 130L, 120L, 140L)) { seg, _ ->
                val idx = seg.map { strokes.indexOf(it) }
                listOf(when (idx) {
                    listOf(0, 1), listOf(2, 3) -> HandwritingCandidate("p", .9f)
                    listOf(0, 1, 2, 3) -> HandwritingCandidate("P", .89f)
                    else -> HandwritingCandidate("1", .5f)
                })
            }
        assertEquals(listOf("p", "p"), result.segments.map { it.candidates.first().char })
    }
}
