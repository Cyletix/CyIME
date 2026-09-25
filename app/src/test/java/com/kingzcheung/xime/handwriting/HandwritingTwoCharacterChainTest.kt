package com.kingzcheung.xime.handwriting

import com.kingzcheung.xime.ui.keyboard.HandwritingCandidateQueue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 复现「连续写两个汉字，候选栏出现“第1/3字”」的数据链（只钉当前行为，不改 UI）。
 *
 * 链路：strokes → [OverlappedHandwritingRecognizer.recognize]（DP 切分）→
 * [HandwritingCandidateQueue.append]（每个段一条）→ 候选栏 “第1/{queue.size}字” → select / confirmAll 上屏。
 *
 * 两个放大点：
 * 1) 识别器：单字被 DP 切成两个“部件成字”后，合并段分数不足时切分被保留（见用例 3）；
 * 2) 队列：把每次识别的**每个段**都当成一个字入队，且没有任何跨轮次纠正机会 ——
 *    `HandwritingInputSession.schedule()` 每轮识别后清空 strokes（第 118 行 `strokes = emptyList()`），
 *    识别器又是每轮新建实例（`OverlappedHandwritingRecognizer()` 在 recognize lambda 内），
 *    于是类文档里“写完后合并段分数更高会自动切回单段”的纠正永远不会发生。
 */
class HandwritingTwoCharacterChainTest {

    // 与 HandwritingCandidateQueueTest 同口径：Segment(startStroke, strokeCount, candidates)
    private fun segment(vararg chars: String) =
        OverlappedHandwritingRecognizer.Segment(0, 1, chars.map { HandwritingCandidate(it, 0.5f) })

    private fun stroke(x: Float) = listOf(x to 0f, x to 1f, x to 2f)

    /** 基线：两轮各出一个字 → 队列 2 条，候选栏显示“第1/2字”。 */
    @Test
    fun twoSeparateRoundsQueueTwoCharacters() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("你")))
        queue.append(listOf(segment("好")))
        assertEquals(2, queue.size)
        assertEquals(listOf("你"), queue.candidates) // 候选栏只展示队首（先写的字）
        assertEquals("你好", queue.confirmAll())
    }

    /** 复现“第1/3字”：第二轮（第二个字）被切成两段 → 队列累计 3 条。 */
    @Test
    fun oneRoundSplitIntoTwoPartsShowsThreeForTwoWrittenCharacters() {
        val queue = HandwritingCandidateQueue()
        queue.append(listOf(segment("你")))                // 第一轮：写第 1 个字
        queue.append(listOf(segment("女"), segment("子"))) // 第二轮：第 2 个字被切成部件
        assertEquals(3, queue.size)                        // ← 候选栏“第1/3字”里的 3
        assertEquals(listOf("你"), queue.candidates)       // 展示的仍是队首候选，不是刚写的字
        assertEquals("你女子", queue.confirmAll())          // 上屏 3 个字，而用户只写了 2 个字
    }

    /**
     * 识别器侧复现：单独一轮里一个字就能被切成 2 段。
     * 注入的 predictFn：单笔段是“自信的部件字”，整窗（两笔合并成一个字）分数很低。
     */
    @Test
    fun singleCharacterCanBeSplitIntoTwoSegmentsByTheRecognizer() {
        val recognizer = OverlappedHandwritingRecognizer()
        val strokes = listOf(stroke(0f), stroke(1f))
        val result = recognizer.recognize(strokes, gaps = listOf(0L, 0L)) { strokesInSegment, _ ->
            if (strokesInSegment.size == 1) {
                listOf(HandwritingCandidate("女", 0.9f), HandwritingCandidate("子", 0.8f))
            } else {
                // 合并段分数低于 MERGED_CHAR_MIN_SCORE(0.35)：不会触发“按单字处理”
                listOf(HandwritingCandidate("好", 0.1f))
            }
        }
        assertEquals(2, result.segments.size)
        assertEquals(0.9f, result.segments[0].candidates.first().score, 0.001f)
        assertEquals(0.9f, result.segments[1].candidates.first().score, 0.001f)
    }
}
