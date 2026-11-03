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

/**
 * The tool-panel focus flag after its field gains or loses view focus. While clipboard search is
 * open its field is the only place keys go, so a lost view focus (switching layout from the
 * toolbar, 10-30) must not send typing to the app behind it; only closing search ends it.
 */
internal fun InputUIState.toolPanelFocusAfter(viewFocused: Boolean): Boolean = viewFocused || clipboardSearchVisible

internal val InputUIState.inputTarget: InputTarget get() = when {
    quickSendFormFocused -> InputTarget.QuickSend(code = quickSendCodeFocused)
    toolPanelInputFocused -> if (clipboardSearchVisible) InputTarget.ClipboardSearch else InputTarget.ToolPanel
    else -> InputTarget.Host
}

/**
 * 键盘自己的输入框（新增固定、剪贴板搜索、工具面板）里点一下删除删哪一段，和应用里的退格一样：
 * 有选区删选区，否则删光标前一个完整的字（表情、国旗、带变体的字不拆成半个）。没东西可删时为 null。
 */
internal fun internalBackspaceRange(text: CharSequence, selectionStart: Int, selectionEnd: Int): IntRange? {
    // 往回拖出的选区起点在终点后面；没有光标时是 -1。
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
    if (end > start) return start until end
    if (start == 0) return null
    // 只看光标前一小段：长草稿每删一下不整段拷贝；最长的组合表情也远短于这个窗口。
    val windowStart = (start - 64).coerceAtLeast(0)
    val window = text.subSequence(windowStart, start).toString()
    val breaks = java.text.BreakIterator.getCharacterInstance()
    breaks.setText(window)
    val previous = breaks.preceding(window.length).takeIf { it != java.text.BreakIterator.DONE } ?: (window.length - 1)
    return (windowStart + previous) until start
}
