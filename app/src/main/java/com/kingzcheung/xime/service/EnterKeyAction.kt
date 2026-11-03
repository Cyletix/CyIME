package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * 回车、Ctrl+回车的唯一规则；屏幕键盘、实体键盘、直接上屏的输入框都走这里，回车键面也按它写。
 *
 * 回车：键面写什么就做什么。输入框有动作（发送、搜索、前往、下一项、完成）且没标「回车不做动作」，
 * 执行这个动作；否则换行。多行文本框的换行直接上屏 "\n"，不再发模拟回车键：很多聊天软件把收到的回车键
 * 当实体键盘的「发送」，键上写着换行、软件里设的也是换行，照样发出去（用户 11-03）。Gboard 同样上屏 "\n"。
 * 终端、单行框、不收文字的输入框仍发回车键。
 *
 * Ctrl+回车：发送。输入框声明了「发送」就执行发送（多行框也一样）；回车本来就执行动作的照回车；
 * 其他输入框发 Ctrl+回车键，由应用自己发送（和 Ctrl+字母一样交给应用）。
 */
internal object EnterKeyAction {
    sealed interface Plan
    data class EditorAction(val action: Int) : Plan
    /** 上屏 "\n"。 */
    data object Newline : Plan
    /** 发回车键。 */
    data object EnterKey : Plan
    /** 发 Ctrl+回车键。 */
    data object CtrlEnterKey : Plan

    private val actions = setOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
        EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE)

    /** 回车执行的输入框动作；换行时为 null。 */
    fun editorAction(imeOptions: Int): Int? {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return action.takeIf { imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 && it in actions }
    }

    fun enter(imeOptions: Int, inputType: Int, terminal: Boolean): Plan {
        editorAction(imeOptions)?.let { return EditorAction(it) }
        return if (!terminal && isMultiLineText(inputType)) Newline else EnterKey
    }

    fun ctrlEnter(imeOptions: Int, inputType: Int, terminal: Boolean): Plan {
        if (terminal) return CtrlEnterKey
        if (imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND) return EditorAction(EditorInfo.IME_ACTION_SEND)
        return editorAction(imeOptions)?.let(::EditorAction) ?: CtrlEnterKey
    }

    /** 回车键面上的动作名，和 [enter] 同一判断。 */
    fun label(imeOptions: Int): String = when (editorAction(imeOptions)) {
        EditorInfo.IME_ACTION_GO -> "前往"
        EditorInfo.IME_ACTION_SEARCH -> "搜索"
        EditorInfo.IME_ACTION_SEND -> "发送"
        EditorInfo.IME_ACTION_NEXT -> "下一项"
        EditorInfo.IME_ACTION_DONE -> "完成"
        else -> "换行"
    }

    fun isMultiLineText(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and (InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0
}
