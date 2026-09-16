package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExpandedCandidatePager 纯逻辑单测：切页边界、行装填、过滤、全局索引一致性。
 */
class ExpandedCandidatePagerTest {

    private fun candidate(text: String, comment: String = "") =
        RimeCandidate(text = text, comment = comment)

    // ── estimateItemUnits ──

    @Test
    fun `中文字符宽度大于拉丁字符`() {
        assertEquals(1.0f, ExpandedCandidatePager.charUnits('中'), 0.001f)
        assertTrue(ExpandedCandidatePager.charUnits('a') < 1.0f)
    }

    @Test
    fun `注释增加条目宽度`() {
        val bare = ExpandedCandidatePager.estimateItemUnits(candidate("字"))
        val withComment = ExpandedCandidatePager.estimateItemUnits(candidate("字", "hao"))
        assertTrue(withComment > bare)
    }

    // ── filterIndices ──

    @Test
    fun `不过滤时返回全部索引`() {
        val all = listOf(candidate("你"), candidate("你好"), candidate("世界"))
        assertEquals(listOf(0, 1, 2), ExpandedCandidatePager.filterIndices(all, singleCharOnly = false))
    }

    @Test
    fun `单字过滤只保留长度为1的候选`() {
        val all = listOf(candidate("你"), candidate("你好"), candidate("世"), candidate("世界", "shij"))
        assertEquals(listOf(0, 2), ExpandedCandidatePager.filterIndices(all, singleCharOnly = true))
    }

    // ── pageSlice ──

    @Test
    fun `空列表返回空页且无下一页`() {
        val page = ExpandedCandidatePager.pageSlice(emptyList(), 0, 4, 100f, emptyList())
        assertTrue(page.globalIndices.isEmpty())
        assertEquals(-1, page.nextStart)
        assertFalse(page.hasNext)
    }

    @Test
    fun `起点越界返回空页`() {
        val all = listOf(candidate("你"), candidate("好"))
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        val page = ExpandedCandidatePager.pageSlice(filtered, 5, 4, 100f, all)
        assertTrue(page.globalIndices.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test
    fun `条目全部装进一页时无下一页`() {
        val all = listOf(candidate("你"), candidate("好"), candidate("吗"))
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        val page = ExpandedCandidatePager.pageSlice(filtered, 0, 4, 1000f, all)
        assertEquals(listOf(0, 1, 2), page.globalIndices)
        assertEquals(-1, page.nextStart)
        assertFalse(page.hasNext)
    }

    @Test
    fun `行装满后换行`() {
        // 行容量 6 单位："中中" = 1.2 + 2 = 3.2；再放一个"中中"超宽 → 换行
        val all = listOf(candidate("中中"), candidate("中中"), candidate("中中"))
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        // 2 行 × 每行 1 条：第 2 行放下第 2 条后，第 3 条触发换行且行数用尽 → 断页
        val page = ExpandedCandidatePager.pageSlice(filtered, 0, 2, 6f, all)
        assertEquals(listOf(0, 1), page.globalIndices)
        assertEquals(2, page.nextStart)
        assertTrue(page.hasNext)
    }

    @Test
    fun `从中间起点切页的全局索引正确`() {
        val all = (1..6).map { candidate("字$it") }
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        val page = ExpandedCandidatePager.pageSlice(filtered, 3, 1, 3.5f, all)
        assertEquals(listOf(3), page.globalIndices)
    }

    @Test
    fun `单字过滤后切页的全局索引指向原始列表`() {
        val all = listOf(candidate("你"), candidate("你好"), candidate("世"), candidate("们好"))
        val filtered = ExpandedCandidatePager.filterIndices(all, singleCharOnly = true)
        val page = ExpandedCandidatePager.pageSlice(filtered, 0, 4, 100f, all)
        // 过滤后只剩全局索引 0、2
        assertEquals(listOf(0, 2), page.globalIndices)
        assertEquals(-1, page.nextStart)
    }

    @Test
    fun `fromIndex偏移跳过候选栏已显示的候选`() {
        val all = listOf(candidate("你"), candidate("你好"), candidate("世"), candidate("们好"))
        // 候选栏已显示前 2 个，展开页从索引 2 开始
        val filtered = ExpandedCandidatePager.filterIndices(all, singleCharOnly = false, fromIndex = 2)
        assertEquals(listOf(2, 3), filtered)
        // 偏移作用于过滤后列表：跳过 1 个单字"你"后接"世"
        val single = ExpandedCandidatePager.filterIndices(all, singleCharOnly = true, fromIndex = 1)
        assertEquals(listOf(2), single)
        // 偏移越界安全
        assertEquals(emptyList<Int>(), ExpandedCandidatePager.filterIndices(all, false, fromIndex = 9))
    }

    @Test
    fun `单字筛选时偏移作用于过滤后列表与候选栏单字口径衔接`() {
        // 候选栏（单字口径）显示"你""好"2 个单字后，展开页应无更多单字——
        // 旧语义（先偏移后过滤）会返回 [2]，把候选栏已显示的"好"再展示一遍
        val all = listOf(candidate("你"), candidate("你好"), candidate("好"), candidate("好的"))
        assertEquals(
            emptyList<Int>(),
            ExpandedCandidatePager.filterIndices(all, singleCharOnly = true, fromIndex = 2)
        )
        // 正向衔接：候选栏显示 2 个单字后，展开页从下一个单字接续
        val all2 = listOf(candidate("你"), candidate("你好"), candidate("好"), candidate("好人"), candidate("们"))
        assertEquals(
            listOf(4),
            ExpandedCandidatePager.filterIndices(all2, singleCharOnly = true, fromIndex = 2)
        )
    }

    @Test
    fun `翻页往返一致：下一页起点作为起点切出的页不重叠`() {
        val all = ('a'..'z').map { candidate(it.toString() + it) }
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        val page1 = ExpandedCandidatePager.pageSlice(filtered, 0, 2, 8f, all)
        assertTrue(page1.hasNext)
        val page2 = ExpandedCandidatePager.pageSlice(filtered, page1.nextStart, 2, 8f, all)
        // 两页条目无重叠
        assertTrue(page1.globalIndices.toSet().intersect(page2.globalIndices.toSet()).isEmpty())
        assertTrue(page2.globalIndices.first() > page1.globalIndices.last())
    }

    @Test
    fun `超宽单条独占一行仍被收录`() {
        val long = candidate("超".repeat(50))
        val all = listOf(long, candidate("字"))
        val filtered = ExpandedCandidatePager.filterIndices(all, false)
        val page = ExpandedCandidatePager.pageSlice(filtered, 0, 2, 5f, all)
        // 超宽条目独占第 1 行，"字"进入第 2 行
        assertEquals(listOf(0, 1), page.globalIndices)
    }

    // ── rowsForArea / estimateRowCount ──

    @Test
    fun `行数随区域高度增长且不低于1`() {
        val density = 3f
        val scaled = 3f
        val small = ExpandedCandidatePager.rowsForArea(200f, density, scaled)
        val big = ExpandedCandidatePager.rowsForArea(1200f, density, scaled)
        val tiny = ExpandedCandidatePager.rowsForArea(1f, density, scaled)
        assertTrue(big > small)
        assertEquals(1, tiny)
    }

    @Test
    fun `底部留白减少可用行数`() {
        val density = 3f
        val scaled = 3f
        val noPadding = ExpandedCandidatePager.rowsForArea(900f, density, scaled, 0f)
        val withPadding = ExpandedCandidatePager.rowsForArea(900f, density, scaled, 120f)
        assertTrue(withPadding < noPadding)
    }

    @Test
    fun `联想行数估算与切页行为一致`() {
        val texts = listOf("词", "词词词", "词", "词词")
        val rows = ExpandedCandidatePager.estimateTextRowCount(texts, 8f)
        // 手工验证：1.2+1=2.2、+3=5.2、+2.2=7.4、第4个超8 → 2 行
        assertEquals(2, rows)
        assertEquals(0, ExpandedCandidatePager.estimateTextRowCount(emptyList(), 8f))
    }
}
