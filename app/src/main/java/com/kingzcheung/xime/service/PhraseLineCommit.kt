package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection

/**
 * 多行短语逐行上屏：第一行照常替换输入框里的拼音编码，之后每行先上屏一个换行（和按回车键一样）再上屏这一行。
 * 整段一次 commitText 时，三星笔记这类富文本框会把编码留在前面、把第一行在结尾再补一遍、光标停在中间（11-05 用户截图）；
 * 一行一行上屏是这些输入框天天走的路。整段算一次编辑。
 */
internal fun InputConnection.commitPhraseLines(text: String): Boolean {
    val lines = text.split('\n')
    beginBatchEdit()
    try {
        // 第一行即使是空的也要上屏：它替换掉组合区里的编码。
        if (!commitText(lines.first(), 1)) return false
        for (line in lines.drop(1)) {
            if (!commitText("\n", 1)) return false
            if (line.isNotEmpty() && !commitText(line, 1)) return false
        }
        return true
    } finally {
        endBatchEdit()
    }
}
