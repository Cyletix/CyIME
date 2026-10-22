package com.kingzcheung.xime.service

import android.widget.EditText

/**
 * Where typed and committed text goes: the host editor or one of the keyboard's own editors.
 * Clipboard search types through the tool-panel editor. When both internal flags are set, quick
 * send wins, as the commit and cursor paths always did.
 */
internal sealed interface InputTarget {
    object Host : InputTarget
    data class QuickSend(val code: Boolean) : InputTarget
    object ToolPanel : InputTarget
    object ClipboardSearch : InputTarget

    val isInternal: Boolean get() = this !== Host

    /** The keyboard's own editor for this target; null for the host or a panel not composed yet. */
    fun editText(): EditText? = when (this) {
        Host -> null
        is QuickSend -> if (code) QuickSendFormCodeEditTextHolder.editText else QuickSendFormEditTextHolder.editText
        ToolPanel, ClipboardSearch -> ToolPanelEditTextHolder.editText
    }
}

internal val InputUIState.inputTarget: InputTarget get() = when {
    quickSendFormFocused -> InputTarget.QuickSend(code = quickSendCodeFocused)
    toolPanelInputFocused -> if (clipboardSearchVisible) InputTarget.ClipboardSearch else InputTarget.ToolPanel
    else -> InputTarget.Host
}
