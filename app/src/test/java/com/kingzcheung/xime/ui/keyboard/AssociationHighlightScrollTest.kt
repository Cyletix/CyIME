package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 九键联想栏「下一项」移动高亮：已完整露出就不滚（用户 10-30：原来每按一下都把高亮滚到最左，像翻页，看着永远是第一个），
 * 移出栏外只滚到刚好露出。视口 0..300，每个联想词 60 宽、间距 4。
 */
class AssociationHighlightScrollTest {
    private val spacing = 4
    private fun spans(first: Int, count: Int, shift: Int = 0) =
        (first until first + count).map { CandidateItemSpan(it, shift + (it - first) * 64, shift + (it - first) * 64 + 60) }

    @Test
    fun `moving to a fully visible association does not scroll`() {
        val visible = spans(0, 5)   // 0..60, 64..124, … 256..316（第 5 个露出一部分）
        assertEquals(0, candidateRevealDelta(0, visible, 0, 300, spacing))
        assertEquals(0, candidateRevealDelta(1, visible, 0, 300, spacing))
        assertEquals(0, candidateRevealDelta(3, visible, 0, 300, spacing))
    }

    @Test
    fun `a partly hidden association scrolls only enough to show it`() {
        val visible = spans(0, 5)
        assertEquals(16, candidateRevealDelta(4, visible, 0, 300, spacing))
        // 往回：前面露一半的那项滚回来刚好露出。
        assertEquals(-20, candidateRevealDelta(2, spans(2, 5, shift = -20), 0, 300, spacing))
    }

    @Test
    fun `the next association not laid out yet first peeks in`() {
        val visible = spans(0, 5)   // 最后一个 4 的右边到 316
        assertEquals(16 + spacing + 1, candidateRevealDelta(5, visible, 0, 300, spacing))
    }

    @Test
    fun `wrapping around jumps to the item`() {
        assertNull(candidateRevealDelta(0, spans(6, 5), 0, 300, spacing))
        assertNull(candidateRevealDelta(9, spans(0, 5), 0, 300, spacing))
        assertNull(candidateRevealDelta(1, emptyList(), 0, 300, spacing))
    }

    @Test
    fun `an item wider than the bar lines up with the start`() {
        val wide = listOf(CandidateItemSpan(0, -10, 50), CandidateItemSpan(1, 54, 454))
        assertEquals(54, candidateRevealDelta(1, wide, 0, 300, spacing))
    }
}
