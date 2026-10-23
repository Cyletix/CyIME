package com.kingzcheung.xime.service

/**
 * Quick-send form transitions. The form's fields in [InputUIState] change only here, so every way
 * of opening it (new, edit, OCR review) and every way of closing it (its button, the tool panel,
 * the window hiding, the compact toolbar) leaves the same state behind.
 */
internal object QuickSendTransitions {
    fun open(state: InputUIState, itemId: Long? = null, text: String = "", code: String = "", ocr: Boolean = false) =
        state.copy(
            showQuickSendForm = true, ocrDraft = ocr,
            quickSendFormFocused = true, quickSendCodeFocused = false,
            quickSendEditingItemId = itemId, quickSendEditingItemText = text, quickSendEditingItemCode = code,
            enterKeyText = "换行",
        )

    fun close(state: InputUIState, enterKeyText: String) = state.copy(
        showQuickSendForm = false, ocrDraft = false,
        quickSendFormFocused = false, quickSendCodeFocused = false,
        quickSendEditingItemId = null, quickSendEditingItemText = "", quickSendEditingItemCode = "",
        enterKeyText = enterKeyText,
    )

    /** Draft text always uses a newline key; focus changes never submit the form. */
    fun textFocus(state: InputUIState, focused: Boolean) =
        if (focused) state.copy(quickSendFormFocused = true, quickSendCodeFocused = false, enterKeyText = "换行")
        else state.copy(quickSendFormFocused = false, quickSendCodeFocused = false)

    /** Enter in the one-line code field only returns to the text, never saves. */
    fun codeFocus(state: InputUIState, focused: Boolean) =
        if (focused) state.copy(quickSendFormFocused = true, quickSendCodeFocused = true, enterKeyText = "下一步")
        else state.copy(quickSendCodeFocused = false)
}

/**
 * Opens the quick-send form. Only one keyboard editor takes typed text at a time: clipboard search
 * and the tool panel close, and an overlay (the clipboard board) is left, since its full-screen
 * layer would swallow the form's touches.
 */
internal fun XimeInputMethodService.openQuickSend(itemId: Long? = null, text: String = "", code: String = "", ocr: Boolean = false) {
    closeClipboardSearch(openBoard = false)
    closeToolPanel()
    keyboardViewModel.closeOverlay()
    uiState.value = QuickSendTransitions.open(uiState.value, itemId, text, code, ocr)
}

/** Closes the quick-send form if it is open; its editors no longer take input. */
internal fun XimeInputMethodService.closeQuickSend() {
    val state = uiState.value
    if (!state.showQuickSendForm && !state.quickSendFormFocused) return
    uiState.value = QuickSendTransitions.close(state, editorEnterKeyText())
    QuickSendFormEditTextHolder.editText = null
    QuickSendFormCodeEditTextHolder.editText = null
}
