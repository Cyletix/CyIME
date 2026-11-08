package com.kingzcheung.xime.service

import android.text.Spanned
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Replace the typed expression in one write. A moved caret or an unreadable editor keeps its text. */
internal fun InputConnection.commitCalculatorCandidate(expression: String, replacement: String): Boolean =
    commitVerifiedCandidate(expression, replacement, keepPrefix = replacement.startsWith(expression + "="))

internal fun InputConnection.commitVerifiedCandidate(expression: String, replacement: String, keepPrefix: Boolean = false): Boolean {
    if (expression.isEmpty() || replacement.isEmpty()) return false
    val host = (this as? TrackedInputConnection)?.base ?: this
    val flags = InputConnection.GET_TEXT_WITH_STYLES
    return runCatching {
        beginBatchEdit()
        try {
            val window = host.readSurroundingWindow(expression.length, 0, flags)?.takeIf { it.startOffset >= 0 }
                ?: host.getExtractedText(ExtractedTextRequest().apply { this.flags = flags }, 0)
                ?: return false
            val text = window.text ?: return false
            val end = window.selectionEnd
            val start = end - expression.length
            if (window.startOffset < 0 || window.selectionStart != end || end !in expression.length..text.length ||
                text.subSequence(start, end).toString() != expression) return false
            if (text is Spanned && text.getSpans(0, text.length, Any::class.java)
                    .any { text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }) return false

            if (keepPrefix) return commitText(replacement.substring(expression.length), 1)
            val caret = window.startOffset + end
            if (!setSelection(window.startOffset + start, caret)) return false
            // Some editors acknowledge selection without applying it, others report it only through ExtractedText.
            fun expressionIsSelected(): Boolean {
                val selectedText = runCatching { host.getSelectedText(0) }.getOrNull()
                if (selectedText != null) return selectedText.toString() == expression
                val selection = host.getExtractedText(ExtractedTextRequest(), 0) ?: return false
                val value = selection.text ?: return false
                return selection.startOffset >= 0 && selection.selectionStart in 0..value.length &&
                    selection.selectionEnd in selection.selectionStart..value.length &&
                    selection.startOffset + selection.selectionStart == caret - expression.length &&
                    selection.startOffset + selection.selectionEnd == caret &&
                    value.subSequence(selection.selectionStart, selection.selectionEnd).toString() == expression
            }
            if (!expressionIsSelected()) return false
            var committed = false
            try {
                committed = commitText(replacement, 1)
                committed
            } finally {
                if (!committed && expressionIsSelected()) setSelection(caret, caret)
            }
        } finally {
            endBatchEdit()
        }
    }.getOrDefault(false)
}
