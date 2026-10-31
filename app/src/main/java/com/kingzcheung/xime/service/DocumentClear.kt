package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.yield

/**
 * 空闲态“上滑清空”清的是整个输入框：光标前、选中的、光标后（审计 F06：以前只删光标前，`abc|def` 清完剩 `def`）。
 * 输入框太长读不全时，[remainingBefore]/[remainingAfter] 是清完后光标两边还剩的字。
 */
internal data class ClearedDocument(
    val before: String,
    val selected: String,
    val after: String,
    val remainingBefore: String = "",
    val remainingAfter: String = "",
) {
    val text: String get() = before + selected + after

    /** “下滑撤回”只在清空后的输入框没被动过时恢复，否则会插到别处（审计 F06）。 */
    fun canRestoreInto(currentBefore: String?, currentAfter: String?): Boolean =
        text.isNotEmpty() && currentBefore == remainingBefore && currentAfter == remainingAfter

    /** 恢复后的选区（光标）相对恢复起点的位置；与清空前相同。 */
    val restoredSelection: IntRange get() = before.length..(before.length + selected.length)
}

internal fun InputConnection.readDocumentForClear(limit: Int): ClearedDocument? {
    val before = getTextBeforeCursor(limit, 0)?.toString() ?: return null
    val after = getTextAfterCursor(limit, 0)?.toString() ?: return null
    val selected = getSelectedText(0)?.toString().orEmpty()
    return ClearedDocument(before, selected, after)
}

/**
 * 每轮最多删光标两边各 limit 个字；超过的长文本一轮删不完，要接着删（用户 2026-10-31：
 * 以前只删一轮，26 万字以上的输入框要按好几次）。这是防止编辑器假装删了却没删时死循环的上限。
 */
internal const val MAX_CLEAR_ROUNDS = 64

/** 清掉的字超过这么多就不留“下滑撤回”，免得几百万字一直占着内存（约 4 MB）。 */
internal const val CLEAR_UNDO_LIMIT = 2_097_152

/**
 * 删掉整个输入框：先删 [document] 读到的，再一轮轮删光标两边剩下的，直到两边都空。
 * 轮与轮之间让出主线程，长文本不卡界面；[stillCurrent] 为假（换了输入框）就停。
 * 返回清掉的全部文字（供撤回，含清完后光标两边还剩的字）；没删成或超过 [undoLimit] 时为 null。
 */
internal suspend fun InputConnection.clearDocument(
    document: ClearedDocument,
    limit: Int,
    undoLimit: Int = CLEAR_UNDO_LIMIT,
    stillCurrent: () -> Boolean = { true },
): ClearedDocument? {
    if (document.text.isEmpty()) return null
    beginBatchEdit()
    try {
        if (document.selected.isNotEmpty() && !commitText("", 1)) return null
        if ((document.before.isNotEmpty() || document.after.isNotEmpty()) &&
            !deleteSurroundingText(document.before.length, document.after.length)) return null
    } finally {
        endBatchEdit()
    }
    // 光标前的块越删越靠前，按删的顺序收起来最后倒过来拼；光标后的块直接往后接。
    val beforeChunks = mutableListOf(document.before)
    val after = StringBuilder(document.after)
    var cleared = document.text.length
    var keepUndo = cleared <= undoLimit
    var rounds = 1
    while (rounds < MAX_CLEAR_ROUNDS) {
        yield()
        if (!stillCurrent()) break
        val nextBefore = getTextBeforeCursor(limit, 0)?.toString().orEmpty()
        val nextAfter = getTextAfterCursor(limit, 0)?.toString().orEmpty()
        if (nextBefore.isEmpty() && nextAfter.isEmpty()) break
        if (!deleteSurroundingText(nextBefore.length, nextAfter.length)) break
        rounds++
        cleared += nextBefore.length + nextAfter.length
        if (keepUndo && cleared > undoLimit) {
            keepUndo = false
            beforeChunks.clear(); after.setLength(0)
        }
        if (keepUndo) { beforeChunks += nextBefore; after.append(nextAfter) }
    }
    if (!keepUndo) return null
    return ClearedDocument(
        before = beforeChunks.asReversed().joinToString(""),
        selected = document.selected,
        after = after.toString(),
        remainingBefore = getTextBeforeCursor(limit, 0)?.toString().orEmpty(),
        remainingAfter = getTextAfterCursor(limit, 0)?.toString().orEmpty(),
    )
}

/** 原位放回清掉的文字，光标/选区回到清空前的位置；输入框已被改过时不动。 */
internal fun InputConnection.restoreDocument(document: ClearedDocument, limit: Int): Boolean {
    val before = getTextBeforeCursor(limit, 0)?.toString()
    val after = getTextAfterCursor(limit, 0)?.toString()
    if (!document.canRestoreInto(before, after)) return false
    beginBatchEdit()
    try {
        // 一次提交太长会超出跨进程传输上限，按 limit 分段放回；不把一个字的两半拆到两段。
        val text = document.text
        var start = 0
        while (start < text.length) {
            var end = minOf(text.length, start + limit)
            if (end < text.length && end - start > 1 && Character.isHighSurrogate(text[end - 1])) end--
            if (!commitText(text.substring(start, end), 1)) return false
            start = end
        }
        // 剩下的字在光标前时不知道绝对位置，光标留在恢复内容末尾；整框都清掉的常见情况恢复原选区。
        if (document.remainingBefore.isEmpty()) {
            val selection = document.restoredSelection
            setSelection(selection.first, selection.last)
        }
    } finally {
        endBatchEdit()
    }
    return true
}
