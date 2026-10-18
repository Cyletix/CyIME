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
