package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 复制后候选栏：前面短胶囊放提取出的链接、长数字，后面长胶囊放原文（用户 11-08）。 */
class ClipboardCapsuleWidthsTest {
    @Test fun nothingExtractedKeepsTheSingleCapsule() {
        assertEquals(0 to 400, clipboardCapsuleWidths(1000, 20, 0, 400))
        assertEquals(0 to 1000, clipboardCapsuleWidths(1000, 20, 0, 2000))
    }

    @Test fun bothFitAtTheirOwnWidth() {
        assertEquals(200 to 400, clipboardCapsuleWidths(1000, 20, 200, 400))
    }

    @Test fun longContentScrollsInsideItsCapsule() {
        // 都很长：提取胶囊最多 40%，其余给原文。
        assertEquals(392 to 588, clipboardCapsuleWidths(1000, 20, 900, 2000))
        // 提取的短、原文长：提取的照内容宽，原文拿剩下的。
        assertEquals(200 to 780, clipboardCapsuleWidths(1000, 20, 200, 2000))
        // 原文短（少见）：提取胶囊可以多占，原文不被压窄。
        assertEquals(680 to 300, clipboardCapsuleWidths(1000, 20, 900, 300))
    }

    @Test fun clipboardDisplayCarriesTheSplit() {
        val state = CandidateBarState.from(
            candidates = listOf("https://example.com/a", "原文 https://example.com/a?utm_source=x"),
            candidateComments = emptyList(), inputText = "", isComposing = false,
            associationCandidates = emptyList(), isShowingRecentClipboard = true, hasNextPage = false,
            clipboardExtractedCount = 1)
        assertEquals(1, (state as CandidateBarState.ClipboardDisplay).extractedCount)
        // 数目越界时夹到候选数以内。
        val clamped = CandidateBarState.from(candidates = listOf("原文"), candidateComments = emptyList(),
            inputText = "", isComposing = false, associationCandidates = emptyList(),
            isShowingRecentClipboard = true, hasNextPage = false, clipboardExtractedCount = 5)
        assertEquals(1, (clamped as CandidateBarState.ClipboardDisplay).extractedCount)
    }
}
