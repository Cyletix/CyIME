package com.kingzcheung.xime.settings

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * 当前是否无痕：用户手动开启，或当前输入框为密码 / 宿主声明 IME_FLAG_NO_PERSONALIZED_LEARNING。
 * 无痕时不学新词、不调词频、不记联想与剪贴板历史、不派发打字统计；已有数据照常使用。
 * 主线程写、引擎线程与剪贴板监听读，volatile 保证可见性。
 */
object IncognitoState {
    @Volatile var manual: Boolean = false
    @Volatile var sensitiveEditor: Boolean = false
    /**
     * Clipboard search is open: what is typed into it is never learned or sent to plugins. Kept
     * apart from [sensitiveEditor], which every new input field rewrites.
     */
    @Volatile var clipboardSearch: Boolean = false
    val active: Boolean get() = manual || sensitiveEditor || clipboardSearch

    fun isSensitiveEditor(attribute: EditorInfo?): Boolean {
        if (attribute == null) return false
        val cls = attribute.inputType and InputType.TYPE_MASK_CLASS
        val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation in intArrayOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        val noPersonalized = attribute.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        return isPassword || noPersonalized
    }
}
