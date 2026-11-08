package com.kingzcheung.xime.service

/** Runs in the input FIFO before this editor's first key, never on the UI thread. */
internal fun prepareEditorEngine(
    currentSchema: String,
    targetSchema: String,
    switchSchema: (String) -> Boolean,
    clearComposition: () -> Unit,
    restoreOptions: () -> Unit,
): Boolean {
    // Re-selecting an unchanged schema rebuilds translators and their dictionaries.
    if (currentSchema != targetSchema && !switchSchema(targetSchema)) return false
    clearComposition()
    restoreOptions()
    return true
}

/** A clipboard notification cannot replace an unfinished word or a key awaiting delivery. */
internal fun canShowClipboardSuggestions(composing: Boolean, pendingEnglish: String, inputPending: Boolean) =
    !composing && pendingEnglish.isEmpty() && !inputPending

/** 实体键盘 Ctrl+V（带不带 Shift 都算）交给应用粘贴；按住不放的重复不是又一次粘贴，Alt、Win 组合另有用处。 */
internal fun isHardwarePasteShortcut(keyCode: Int, ctrl: Boolean, alt: Boolean, meta: Boolean, repeatCount: Int) =
    keyCode == android.view.KeyEvent.KEYCODE_V && ctrl && !alt && !meta && repeatCount == 0
