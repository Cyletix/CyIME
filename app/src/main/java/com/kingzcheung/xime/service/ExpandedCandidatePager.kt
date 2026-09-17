package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate

/**
 * 展开候选数据过滤与展示分行（纯逻辑，可 JVM 单测）。
 *
 * 展开页为可滚动列表（参考 fcitx5 的 Bulk 列表模型）：服务层一次拉全量候选，
 * UI 借助 LazyColumn 只渲染可见行（真机打点：FlexRow 全量非懒测量 356 条耗时
 * 348ms 是展开卡顿根因，拉取本身仅 13ms）。行分组按字符当量估算，仅作展示
 * 分组；此对象还负责候选栏 ↔ 展开页的口径衔接过滤。
 */
object ExpandedCandidatePager {

    /** 条目固定开销：水平 padding(8dp) + 两侧间隙分摊(6dp)，以 18sp 字宽为 1 单位近似 */
    const val ITEM_FIXED_UNITS = 1.2f

    /** 注释字号(11sp)相对主字号(18sp)的宽度系数 */
    private const val COMMENT_WIDTH_FACTOR = 0.62f

    /** 单字符宽度（字符当量）：CJK 约 1 个字宽，其余（拉丁/数字/标点）约 0.55 */
    fun charUnits(c: Char): Float = if (c.code > 0x2E80) 1.0f else 0.55f

    /** 条目估算宽度：候选词全字宽 + 注释按小字号折算 + 固定开销 */
    fun estimateItemUnits(candidate: RimeCandidate): Float {
        var units = ITEM_FIXED_UNITS
        candidate.text.forEach { units += charUnits(it) }
        candidate.comment.forEach { units += charUnits(it) * COMMENT_WIDTH_FACTOR }
        return units
    }

    /**
     * 展开区行容量（字符当量）：可用宽度 ≈ 屏宽 - 左右栏/分隔/内边距合计约 140dp，
     * 以 18sp 主字号为 1 单位。
     */
    fun rowWidthUnits(screenWidthPx: Float, density: Float, scaledDensity: Float): Float =
        ((screenWidthPx - 140f * density) / (18f * scaledDensity)).coerceAtLeast(8f)

    /**
     * 把过滤后索引按字符当量估算贪心分行。
     *
     * 分行结果**仅作展示分组**（配合 LazyColumn 只渲染可见行）：估算与实际文本
     * 宽度存在偏差，但行内条目按 weight 均分宽度自适应拉伸，偏差只改变每行
     * 词数（某行多/少一个词），不会溢出、不影响点选（每条携带全局索引）。
     */
    fun flowRows(
        filtered: List<Int>,
        all: List<RimeCandidate>,
        rowWidthUnits: Float,
    ): List<List<Int>> {
        if (filtered.isEmpty()) return emptyList()
        val rows = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var currentUnits = 0f
        for (index in filtered) {
            val itemUnits = estimateItemUnits(all[index])
            val withItem = if (current.isEmpty()) itemUnits else currentUnits + itemUnits
            if (current.isNotEmpty() && withItem > rowWidthUnits) {
                rows.add(current)
                current = mutableListOf(index)
                currentUnits = itemUnits
            } else {
                current.add(index)
                currentUnits = withItem
            }
        }
        if (current.isNotEmpty()) rows.add(current)
        return rows
    }

    /**
     * 过滤后的全局索引列表。
     * @param singleCharOnly true 时只保留单字候选（筛选单字功能）
     * @param fromIndex 起始偏移：跳过候选栏已显示的前若干个候选，避免展开页重复
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
}
