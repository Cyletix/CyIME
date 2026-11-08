package com.kingzcheung.xime.service

import android.os.Build
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection

/** Query the active editor only on systems that implement the API. Older or partial
 * editor connections use each caller's existing fallback. Offsets are relative to the window. */
internal fun InputConnection.readSurroundingWindow(before: Int, after: Int, flags: Int): ExtractedText? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val window = try {
        getSurroundingText(before, after, flags)
    } catch (_: RuntimeException) {
        null
    } catch (_: AbstractMethodError) {
        null
    } ?: return null
    return ExtractedText().apply {
        text = window.text
        startOffset = window.offset
        selectionStart = window.selectionStart
        selectionEnd = window.selectionEnd
    }
}
