package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 英文手写的词间空格：一段里按空隙分词，停顿后写在右边隔开的新词前补空格，离得近的接着拼。 */
class EnglishWordSpacingTest {
    /** 一个字母：从 [x] 起宽 30、高 [h] 的一竖一横。 */
    private fun letter(x: Float, h: Float = 60f) = listOf(listOf(x to 0f, x to h), listOf(x to h / 2, x + 30f to h / 2))
    private fun word(x: Float, letters: Int, pitch: Float = 36f) = (0 until letters).flatMap { letter(x + it * pitch) }

    @Test fun lettersCloseTogetherStayOneWordAndAWideGapStartsTheNext() {
        // 字母间空 6，词间空 60（行高 60 的 1 倍），一段写了两个词
        val ink = word(0f, 3) + word(166f, 2)
        val chunk = EnglishWordSpacing().next(ink)
        assertFalse(chunk.startsWord)
        assertEquals(listOf((0 until 6).toList(), (6 until 10).toList()), chunk.words)
    }

    @Test fun aChunkWrittenToTheRightAfterAPauseStartsANewWord() {
        val spacing = EnglishWordSpacing()
        spacing.next(word(0f, 3))
        assertTrue(spacing.next(word(166f, 2)).startsWord)
    }

    @Test fun aChunkRightNextToThePreviousOneContinuesTheSameWord() {
        // an|tique：停了一下接着写，左右只隔字母间距
        val spacing = EnglishWordSpacing()
        spacing.next(word(0f, 2))
        assertFalse(spacing.next(word(72f, 5)).startsWord)
    }

    @Test fun startingAgainOnTheLeftIsANewWord() {
        val spacing = EnglishWordSpacing()
        spacing.next(word(400f, 3))
        assertTrue(spacing.next(word(0f, 2)).startsWord)
    }

    @Test fun afterAKeyTheNextChunkGetsNoLeadingSpace() {
        val spacing = EnglishWordSpacing()
        spacing.next(word(0f, 3))
        spacing.reset()
        assertFalse(spacing.next(word(400f, 2)).startsWord)
    }

    @Test fun shortLettersAtTheStartDoNotMakeLetterGapsLookLikeWordGaps() {
        // 先写了三个矮字母（每笔高 30，字母间空 8）：按整段高度算词距只有 6.6，会拆成三个词；
        // 行高至少是单笔高度的 1.6 倍（48），词距 10.6，仍是一个词
        val ink = (0 until 3).map { i -> listOf(i * 28f to 0f, i * 28f + 20f to 30f) }
        assertEquals(3, EnglishWordSpacing.split(ink, 30f * EnglishWordSpacing.WORD_GAP_RATIO).size)
        assertEquals(1, EnglishWordSpacing().next(ink).words.size)
    }

    @Test fun sentenceCandidatesJoinWordsAndSwapTheLeastSureOne() {
        val each = listOf(listOf("the" to .9f, "tho" to .1f), listOf("rood" to .4f, "road" to .35f))
        val out = EnglishWordRecognizer.joinWords(each)
        assertEquals("the rood", out[0].first)
        assertEquals("the road", out[1].first)
    }
}
