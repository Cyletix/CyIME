package com.kingzcheung.xime.service

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Reattach only the exact preedit that this editor previously acknowledged at this position. */
internal fun InlineCompositionTracker.restoreLostSpan(
    connection: InputConnection?, selection: Int, selectionEnd: Int, composingStart: Int, composingEnd: Int,
): DetachedComposition? {
    val region = takeLostSpan(selection, selectionEnd, composingStart, composingEnd) ?: return null
    val ic = connection ?: return null
    val code = text ?: return null
    return try {
        val value = ic.readSurroundingWindow(code.length, code.length, 0)?.takeIf { it.startOffset >= 0 }
            ?: ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 2 * code.length + 2 }, 0)
            ?: return null
        if (value.selectionStart != value.selectionEnd || value.startOffset + value.selectionStart != selection ||
            locateFrozenCompositionAt(code, region.start, value.text, value.startOffset) != region) return null
        region.takeIf { ic.setComposingRegion(it.start, it.end) }
    } catch (_: RuntimeException) {
        null
    }
}
