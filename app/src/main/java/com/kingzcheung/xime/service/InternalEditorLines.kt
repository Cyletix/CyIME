package com.kingzcheung.xime.service

import android.widget.EditText

/**
 * 键盘自己的输入框按它当前的排版移行：上一行/下一行保持横向位置，第一行按上到文首、最后一行按下到文末，
 * 行首/行尾按视觉行（自动折行也算一行），和系统输入框自己处理方向键一样。只在主线程调用。
 */
internal class InternalEditorLines(private val editor: EditText) : EditorCursor.VisualLines {
    override fun verticalTarget(offset: Int, direction: Int): Int? {
        val layout = editor.layout ?: return null
        val length = editor.text?.length ?: return null
        val from = offset.coerceIn(0, length)
        val line = layout.getLineForOffset(from) + direction.coerceIn(-1, 1)
        if (line < 0) return 0
        if (line >= layout.lineCount) return length
        return layout.getOffsetForHorizontal(line, layout.getPrimaryHorizontal(from)).coerceIn(0, length)
    }

    override fun lineBoundary(offset: Int, toEnd: Boolean): Int? {
        val layout = editor.layout ?: return null
        val length = editor.text?.length ?: return null
        val line = layout.getLineForOffset(offset.coerceIn(0, length))
        if (!toEnd) return layout.getLineStart(line)
        val end = layout.getLineEnd(line)
        // 不是最后一行时行尾停在换行符或折行处之前，否则光标会显示到下一行开头。
        return (if (line == layout.lineCount - 1) end else end - 1).coerceIn(layout.getLineStart(line), length)
    }
}
