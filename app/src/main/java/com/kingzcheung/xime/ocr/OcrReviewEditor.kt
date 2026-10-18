package com.kingzcheung.xime.ocr

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.clipboard.ClipboardDraftSaveResult
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.ui.keyboard.QuickSendFormArea

internal fun saveOcrDraft(context: Context, text: String): ClipboardDraftSaveResult {
    if (text.isBlank()) return ClipboardDraftSaveResult.EMPTY_TEXT
    val manager = ClipboardManager.getInstance(context)
    manager.copyToSystemClipboard(text)
    manager.addItem(text)
    return ClipboardDraftSaveResult.ACCEPTED
}

/** The same popup and input routing as clipboard editing; only the draft's save destination differs. */
@Composable
internal fun OcrReviewEditor(text: String, textColor: Color, background: Color, accent: Color,
    onFocus: (Boolean) -> Unit, onSave: (String) -> Unit, onCancel: () -> Unit, onRetake: () -> Unit) {
    QuickSendFormArea(backgroundColor = Color.Transparent, textColor = textColor, accentColor = accent,
        isFocused = true, initialText = text, cardBgColor = background,
        onClose = { edited, _ -> onSave(edited) }, onCancel = onCancel,
        onFocusChange = onFocus, onCodeFocusChange = {}, title = null,
        showCode = false, onRetake = onRetake)
}
