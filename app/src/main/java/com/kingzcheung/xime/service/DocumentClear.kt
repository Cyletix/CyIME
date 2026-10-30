package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection

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

/** 删掉 [document] 读到的全部文字；返回清完后的记录（含光标两边剩下的字），失败为 null。 */
internal fun InputConnection.clearDocument(document: ClearedDocument, limit: Int): ClearedDocument? {
    if (document.text.isEmpty()) return null
    beginBatchEdit()
    try {
        if (document.selected.isNotEmpty() && !commitText("", 1)) return null
        if ((document.before.isNotEmpty() || document.after.isNotEmpty()) &&
            !deleteSurroundingText(document.before.length, document.after.length)) return null
    } finally {
        endBatchEdit()
    }
    return document.copy(
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
        if (!commitText(document.text, 1)) return false
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
