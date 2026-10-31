package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * EditorInfo（inputType）纯函数分类器：判定当前输入框的能力约束，
 * 供键盘行为适配使用（受限框禁英文联想、数字框自动数字键盘）。
 *
 * 线程契约：无状态纯函数，任意线程可调。
 */
internal object EditorInfoClassifier {

    /**
     * 受限输入框：补全/联想类功能应禁用。
     * - 终端（见 [isTerminalEditor]）：宿主要求原始按键语义（如 Termux）
     * - 密码类变体：联想会泄漏前缀，候选"回删替换"会破坏输入
     * - NO_SUGGESTIONS：宿主显式声明不要建议
     */
    fun isRestrictedEditor(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        if (isTerminalEditor(info)) return true
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return true
            }
            val flags = inputType and InputType.TYPE_MASK_FLAGS
            if (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) {
                return true
            }
        }
        if (cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return true
        }
        return false
    }

    /**
     * 秘密输入框：终端（宿主要求原始按键语义，回删替换会破坏输入）
     * 与密码类变体（联想会泄漏输入前缀）。英文联想/回删替换在此类框必须完全禁用。
     * 注意 NO_SUGGESTIONS 不在此列：该 flag 只表示宿主不要系统内联补全，
     * 候选栏英文联想仍应提供（否则搜索框等场景无英文联想，且与退格路径行为不一致）。
     */
    fun isSecretEditor(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        if (isTerminalEditor(info)) return true
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return true
            }
        }
        if (cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return true
        }
        return false
    }

    /**
     * 终端类输入框（Termux、ConnectBot 等 SSH 客户端）：inputType 没有类别位。
     * 包括 TYPE_NULL，以及 Termux「按字符输入」写的 VISIBLE_PASSWORD | NO_SUGGESTIONS（同样不带 TYPE_CLASS_TEXT）。
     * 这类宿主不是文本编辑器：读不到光标前后的文字、不认选区和组合区，只收按键和直接上屏的文字
     * （Android 文档对 TYPE_NULL 的说明：输入法应退到“只发按键”的模式）。
     */
    fun isTerminalEditor(info: EditorInfo?): Boolean =
        info != null && info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    /**
     * 数字类输入框（包含数字密码、电话、日期时间）：适合自动弹出数字键盘。
     */
    fun isNumberEditor(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        if (inputType == InputType.TYPE_NULL) return false
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
    }
}
