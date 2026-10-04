package com.kingzcheung.xime.clipboard

internal enum class ClipboardDraftSaveResult(val errorMessage: String?) {
    ACCEPTED(null),
    EMPTY_TEXT("内容不能为空"),
    MISSING_ITEM("这条记录已不存在，修改内容仍保留在表单中"),
}

/** Validate a draft before dismissing its editor; ACCEPTED is not a persistence receipt. */
internal fun submitClipboardDraft(
    text: String,
    code: String,
    editingItemId: Long?,
    add: (String, String) -> Unit,
    update: (Long, String, String) -> Boolean,
): ClipboardDraftSaveResult {
    if (text.isBlank()) return ClipboardDraftSaveResult.EMPTY_TEXT
    if (editingItemId == null) {
        add(text, code.trim())
        return ClipboardDraftSaveResult.ACCEPTED
    }
    return if (update(editingItemId, text, code.trim())) {
        ClipboardDraftSaveResult.ACCEPTED
    } else {
        ClipboardDraftSaveResult.MISSING_ITEM
    }
}
