package com.kingzcheung.xime.service

import android.text.InputType
import com.kingzcheung.xime.clipboard.VerificationCodeExtractor
import kotlinx.coroutines.delay

/** Reacquire the editor after each digit because split fields replace InputConnection. */
internal object VerificationCodeInput {
    data class Target(val packageName: String, val fieldId: Int, val inputType: Int, val session: Long)

    suspend fun fill(
        code: String,
        target: () -> Target?,
        commit: (String) -> Boolean,
        pause: suspend () -> Unit = { delay(180) },
        content: () -> String? = { null },
    ): Boolean {
        if (!VerificationCodeExtractor.isCode(code)) return false
        val initial = target() ?: return false
        var previous: Target? = null
        var previousContent: String? = null
        for ((index, digit) in code.withIndex()) {
            if (index > 0) pause()
            val current = target() ?: return false
            val currentContent = content()
            if (previous?.session == current.session && previousContent != null && currentContent == previousContent) return false
            if (current.packageName != initial.packageName || current.inputType != initial.inputType) return false
            // A different text editor can be unrelated; only numeric editors may follow focus.
            if ((current.session != initial.session || current.fieldId != initial.fieldId) &&
                current.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_NUMBER) return false
            previous = current
            previousContent = currentContent
            if (!commit(digit.toString())) return false
        }
        return true
    }
}
