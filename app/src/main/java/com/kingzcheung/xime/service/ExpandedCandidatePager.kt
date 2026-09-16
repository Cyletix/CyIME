package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate

/**
 * 候选展开页的本地分页器（纯逻辑，可 JVM 单测）。
 *
 * 分页单位是"行"而非固定条目数：候选词长度不一，每页填满固定行数后条目数
 * 自然浮动，视觉高度稳定。行内装填与 FlexRow 布局同一贪心算法——条目宽度按
 * 字符当量估算（CJK 1.0、拉丁 0.55 个主字号宽），因此数据层切出的页与真实
 * 布局的行断点基本一致（存在少量估算误差，只影响行数±1，不影响正确性）。
 *
 * 翻页模型：一页的起点记在"页起点栈"上（见 KeyboardViewModel），因为条目数
 * 浮动导致上一页起点无法由 start 反推。
 */
object ExpandedCandidatePager {

    /** 条目固定开销：水平 padding(8dp) + 两侧间隙分摊(6dp)，以 18sp 字宽为 1 单位近似 */
    const val ITEM_FIXED_UNITS = 1.2f

    /** 注释字号(11sp)相对主字号(18sp)的宽度系数 */
    private const val COMMENT_WIDTH_FACTOR = 0.62f

    /** 单字符宽度（字符当量）：CJK 约 1 个字宽，其余（拉丁/数字/标点）约 0.55 */
    fun charUnits(c: Char): Float = if (c.code > 0x2E80) 1.0f else 0.55f

    /**
     * 行容量（字符当量）：展开区可用宽度 ≈ 屏宽 - 左右栏/分隔/内边距合计约 140dp，
     * 以 18sp 主字号为 1 单位。UI 层与服务层（硬件翻页键）统一用此换算，保证
     * 数据层切页与布局行断点一致；估算误差只影响行数 ±1，不影响正确性。
     */
    fun rowWidthUnits(screenWidthPx: Float, density: Float, scaledDensity: Float): Float =
        ((screenWidthPx - 140f * density) / (18f * scaledDensity)).coerceAtLeast(8f)

    /**
     * 按展开区实际高度动态得出行数（撑满整页，不硬编码）：
     * 行高 = 条目上下 padding(8dp×2) + 主字号文本行高(约 24sp)，行距 6dp。
     * n 行占 n×行高 + (n-1)×行距 ≤ 可用高度，取最大 n。
     */
    fun rowsForArea(
        areaHeightPx: Float,
        density: Float,
        scaledDensity: Float,
        bottomPaddingPx: Float = 0f,
    ): Int {
        val usable = (areaHeightPx - bottomPaddingPx).coerceAtLeast(0f)
        val itemHeight = 8f * density * 2 + 24f * scaledDensity
        val spacing = 6f * density
        val rows = ((usable + spacing) / (itemHeight + spacing)).toInt()
        return rows.coerceIn(1, 12)
    }

    /** 给定各条目的估算宽度，返回按贪心分行需要的行数（用于为联想区预留行数） */
    fun estimateRowCount(itemUnits: List<Float>, rowWidthUnits: Float): Int {
        if (itemUnits.isEmpty()) return 0
        var rows = 1
        var rowUnits = 0f
        itemUnits.forEach { u ->
            val withItem = if (rowUnits == 0f) u else rowUnits + u
            if (rowUnits > 0f && withItem > rowWidthUnits) {
                rows++
                rowUnits = u
            } else {
                rowUnits = withItem
            }
        }
        return rows
    }

    /** [estimateRowCount] 的文本便捷版（联想词无注释） */
    fun estimateTextRowCount(texts: List<String>, rowWidthUnits: Float): Int =
        estimateRowCount(texts.map { estimateItemUnits(RimeCandidate(it, "")) }, rowWidthUnits)

    /** 条目估算宽度：候选词全字宽 + 注释按小字号折算 + 固定开销 */
    fun estimateItemUnits(candidate: RimeCandidate): Float {
        var units = ITEM_FIXED_UNITS
        candidate.text.forEach { units += charUnits(it) }
        candidate.comment.forEach { units += charUnits(it) * COMMENT_WIDTH_FACTOR }
        return units
    }

    /**
     * 过滤后的全局索引列表。
     * @param singleCharOnly true 时只保留单字候选（筛选单字功能）
     * @param fromIndex 起始偏移：跳过候选栏已显示的前若干个候选，避免展开页重复
     */
    /**
     * 生成展开页的数据索引列表：先按 [singleCharOnly] 口径过滤（false=全量、
     * true=仅单字，与候选栏筛选口径一致），再从 [fromIndex] 起跳过候选栏已
     * 显示的条目数（候选栏与展开页同口径衔接，不重复展示）。
     */
    fun filterIndices(
        all: List<RimeCandidate>,
        singleCharOnly: Boolean,
        fromIndex: Int = 0,
    ): List<Int> {
        val filtered = if (!singleCharOnly) all.indices.toList()
        else all.indices.filter { all[it].text.length == 1 }
        return filtered.drop(fromIndex.coerceIn(0, filtered.size))
    }

    /**
     * 从过滤列表 [filtered] 的 [start] 下标起，按贪心分行收集 [rowsPerPage] 行。
     *
     * @return 本页条目的全局索引与下一页起点（过滤列表下标）；
     *         条目用尽时 [ExpandedPage.nextStart] 为 -1（无下一页）
     */
    fun pageSlice(
        filtered: List<Int>,
        start: Int,
        rowsPerPage: Int,
        rowWidthUnits: Float,
        all: List<RimeCandidate>,
    ): ExpandedPage {
        if (filtered.isEmpty() || start >= filtered.size || start < 0) {
            return ExpandedPage(emptyList(), -1)
        }
        val items = mutableListOf<Int>()
        var index = start
        var rowUnits = 0f
        var rows = 0
        while (index < filtered.size && rows < rowsPerPage) {
            val itemUnits = estimateItemUnits(all[filtered[index]])
            val withItem = if (items.isEmpty()) itemUnits else rowUnits + itemUnits
            if (items.isNotEmpty() && withItem > rowWidthUnits) {
                // 当前行装不下：换行
                rows++
                if (rows >= rowsPerPage) break
                rowUnits = itemUnits
            } else {
                rowUnits = withItem
            }
            items.add(filtered[index])
            index++
        }
        val nextStart = if (index < filtered.size) index else -1
        return ExpandedPage(items, nextStart)
    }

    /**
     * 一页数据：本页条目的全局候选索引 + 下一页起点。
     * 全局索引用于 selectCandidateByGlobalIndex 跨页选词。
     */
    class ExpandedPage(
        val globalIndices: List<Int>,
        val nextStart: Int,
    ) {
        val hasNext: Boolean get() = nextStart != -1
    }
}
