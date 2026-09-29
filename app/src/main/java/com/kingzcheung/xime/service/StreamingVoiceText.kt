package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest

/** 工具栏语音实时上屏；只修订仍在光标前、内容完全匹配的本句尾部。 */
internal class StreamingVoiceText {
    private var previousResult = ""
    private var visibleTail = ""
    private var frozenPrefix = ""
    private var ownedCursor: Int? = null

    fun reset() {
        previousResult = ""
        visibleTail = ""
        frozenPrefix = ""
        ownedCursor = null
    }

    fun update(ic: InputConnection, text: String) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val cursor = extracted?.takeIf { it.startOffset >= 0 && it.selectionEnd >= 0 }
            ?.let { it.startOffset + it.selectionEnd }
        val selection = ic.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            // 用户主动选区期间不能用识别结果覆盖选中文字。
            frozenPrefix = text
            ownedCursor = null
            visibleTail = ""
            previousResult = text
            return
        }
        if (visibleTail.isNotEmpty() && (
                (ownedCursor != null && cursor != null && cursor != ownedCursor) ||
                ic.getTextBeforeCursor(visibleTail.length, 0)?.toString() != visibleTail)) {
            // 用户编辑/移动过光标：已显示前缀保持原样，后续只追加新识别的增量。
            frozenPrefix = previousResult
            ownedCursor = null
            visibleTail = ""
        }
        val next = when {
            frozenPrefix.isEmpty() -> text
            text.startsWith(frozenPrefix) -> text.removePrefix(frozenPrefix)
            else -> {
                // ASR 会修订累计结果的旧前缀。无法可靠区分这次修订与新增文字时，
                // 保留编辑器现状，并以本次结果重新建立增量起点；不能永久等待旧前缀。
                // 此前已上屏的尾部也交还用户，避免下一次更新误删它。
                frozenPrefix = text
                previousResult = text
                visibleTail = ""
                ownedCursor = null
                return
            }
        }
        if (next != visibleTail) {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                if (next.startsWith(visibleTail)) {
                    val added = next.removePrefix(visibleTail)
                    if (added.isNotEmpty()) ic.commitText(added, 1)
                } else {
                    if (visibleTail.isNotEmpty()) ic.deleteSurroundingText(visibleTail.length, 0)
                    if (next.isNotEmpty()) ic.commitText(next, 1)
                }
            } finally { ic.endBatchEdit() }
            ownedCursor = cursor?.let { it + next.length - visibleTail.length }
            visibleTail = next
        }
        previousResult = text
    }
}
