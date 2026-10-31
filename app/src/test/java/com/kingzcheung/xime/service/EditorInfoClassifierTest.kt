package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EditorInfoClassifier 分类判定：
 * - 受限输入框（密码/终端/NO_SUGGESTIONS）→ isRestrictedEditor
 * - 纯数字输入框（数字/电话/日期时间）→ isNumberEditor
 */
class EditorInfoClassifierTest {

    private fun editorInfo(inputType: Int): EditorInfo = EditorInfo().apply { this.inputType = inputType }

    // ── isRestrictedEditor ──

    @Test
    fun `null输入框不算受限`() {
        assertFalse(EditorInfoClassifier.isRestrictedEditor(null))
    }

    @Test
    fun `普通文本框不受限`() {
        assertFalse(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            )
        )
    }

    @Test
    fun `文本密码框受限`() {
        assertTrue(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                )
            )
        )
    }

    @Test
    fun `可见密码框受限`() {
        assertTrue(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
            )
        )
    }

    @Test
    fun `网页密码框受限`() {
        assertTrue(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            )
        )
    }

    @Test
    fun `TYPE_NULL受限_终端场景`() {
        assertTrue(EditorInfoClassifier.isRestrictedEditor(editorInfo(InputType.TYPE_NULL)))
    }

    @Test
    fun `NO_SUGGESTIONS标记受限`() {
        assertTrue(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                )
            )
        )
    }

    @Test
    fun `数字密码框受限`() {
        assertTrue(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                )
            )
        )
    }

    @Test
    fun `邮箱框不受限`() {
        assertFalse(
            EditorInfoClassifier.isRestrictedEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                )
            )
        )
    }

    // ── isSecretEditor ──

    @Test
    fun `null输入框不算秘密框`() {
        assertFalse(EditorInfoClassifier.isSecretEditor(null))
    }

    @Test
    fun `普通文本框不算秘密框`() {
        assertFalse(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            )
        )
    }

    @Test
    fun `NO_SUGGESTIONS不算秘密框_英文联想应放行`() {
        assertFalse(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                )
            )
        )
    }

    // ── isTerminalEditor ──

    @Test
    fun `TYPE_NULL是终端`() {
        assertTrue(EditorInfoClassifier.isTerminalEditor(editorInfo(InputType.TYPE_NULL)))
    }

    @Test
    fun `Termux按字符输入不带类别位也是终端且受限`() {
        // Termux「enforce-char-based-input」写的 inputType：VISIBLE_PASSWORD | NO_SUGGESTIONS，没有 TYPE_CLASS_TEXT。
        val termux = editorInfo(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertTrue(EditorInfoClassifier.isTerminalEditor(termux))
        assertTrue(EditorInfoClassifier.isRestrictedEditor(termux))
        assertTrue(EditorInfoClassifier.isSecretEditor(termux))
        assertFalse(EditorInfoClassifier.isNumberEditor(termux))
    }

    @Test
    fun `带类别位的文本框和密码框都不是终端`() {
        assertFalse(EditorInfoClassifier.isTerminalEditor(null))
        assertFalse(EditorInfoClassifier.isTerminalEditor(editorInfo(InputType.TYPE_CLASS_TEXT)))
        assertFalse(EditorInfoClassifier.isTerminalEditor(editorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)))
        assertFalse(EditorInfoClassifier.isTerminalEditor(editorInfo(InputType.TYPE_CLASS_NUMBER)))
        assertFalse(EditorInfoClassifier.isTerminalEditor(editorInfo(InputType.TYPE_CLASS_PHONE)))
    }

    @Test
    fun `TYPE_NULL算秘密框`() {
        assertTrue(EditorInfoClassifier.isSecretEditor(editorInfo(InputType.TYPE_NULL)))
    }

    @Test
    fun `文本密码框算秘密框`() {
        assertTrue(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                )
            )
        )
    }

    @Test
    fun `可见密码框算秘密框`() {
        assertTrue(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
            )
        )
    }

    @Test
    fun `网页密码框算秘密框`() {
        assertTrue(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            )
        )
    }

    @Test
    fun `数字密码框算秘密框`() {
        assertTrue(
            EditorInfoClassifier.isSecretEditor(
                editorInfo(
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                )
            )
        )
    }

    // ── isNumberEditor ──

    @Test
    fun `null输入框不算数字框`() {
        assertFalse(EditorInfoClassifier.isNumberEditor(null))
    }

    @Test
    fun `数字类输入框识别为数字框`() {
        assertTrue(
            EditorInfoClassifier.isNumberEditor(
                editorInfo(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            )
        )
        assertTrue(EditorInfoClassifier.isNumberEditor(editorInfo(InputType.TYPE_CLASS_NUMBER)))
    }

    @Test
    fun `电话类输入框识别为数字框`() {
        assertTrue(
            EditorInfoClassifier.isNumberEditor(
                editorInfo(InputType.TYPE_CLASS_PHONE)
            )
        )
    }

    @Test
    fun `日期时间类输入框识别为数字框`() {
        assertTrue(
            EditorInfoClassifier.isNumberEditor(
                editorInfo(InputType.TYPE_CLASS_DATETIME)
            )
        )
    }

    @Test
    fun `数字密码框使用数字键盘`() {
        assertTrue(
            EditorInfoClassifier.isNumberEditor(
                editorInfo(
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                )
            )
        )
    }

    @Test
    fun `普通文本框不算数字框`() {
        assertFalse(EditorInfoClassifier.isNumberEditor(editorInfo(InputType.TYPE_CLASS_TEXT)))
    }
}
