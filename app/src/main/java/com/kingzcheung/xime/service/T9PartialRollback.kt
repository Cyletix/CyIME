package com.kingzcheung.xime.service

/**
 * 回滚最近 [count] 个 T9 partial commit 段。
 *
 * 语义边界（2026-09-25 真机 bug 的根因）：
 * - [count] 是 C++ 侧 [com.kingzcheung.xime.rime.RimeEngine.t9GetAndConsumeUndoneRightCommitCount]
 *   给出的**撤销段数**，不是正文字符数。
 * - 这些段只存在于输入法状态（候选栏/输入框 composing 的显示），**从未写入宿主正文**，
 *   因此回滚只能改输入法自己的状态，绝不能用段数或段文本长度去删宿主文本
 *   （历史实现 `deleteBeforeCursor(count)` 会把光标前的正文删掉）。
 *
 * 返回被移除的段（按移除顺序），供调用方回滚用户词典调频与 UI 计数。
 */
internal fun MutableList<T9PartialSegment>.rollbackPartialSegments(count: Int): List<T9PartialSegment> {
    if (count <= 0 || isEmpty()) return emptyList()
    val removed = ArrayList<T9PartialSegment>(count)
    repeat(count) {
        removed += removeLastOrNull() ?: return removed
    }
    return removed
}
