package com.kingzcheung.xime.service

import android.os.SystemClock
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * 全选（编辑面板「全选」、按住 !@# 的 Ctrl+A、九键 Ctrl+4）：先让输入框自己全选（performContextMenuAction），
 * 再读一次输入框核对真的选上了；没选上就按读到的全文长度 setSelection(0, 长度) 补一次。
 * 全选后 [WINDOW_MS] 内宿主把选区收成句尾光标（字没变）也补一次，只补一次，不来回拉扯。
 *
 * #93 / #129 / #158（荣耀 Magic8、Android 17、微信）：全选有时光标跳到句尾、有时没反应，退出聊天再进又不行，
 * 开发机复现不了。其他输入框全选本来就正常时，核对通过，不多做任何事。
 * 补过、补不上、读不到时写一行警告日志（只记长度和选区位置，不记文字；密码框不记），用户可在「日志查看器」分享。
 */
internal class SelectAllGuard(
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val log: (String) -> Unit = {},
) {
    private class Pending(val connection: InputConnection, val text: String, val at: Long, val repaired: Boolean)

    private var pending: Pending? = null

    fun selectAll(connection: InputConnection) {
        pending = null
        val accepted = runCatching { connection.performContextMenuAction(android.R.id.selectAll) }.getOrDefault(false)
        val after = read(connection)
        val text = after?.fullText()
        if (after == null || text == null) {
            // WebView 等读不到全文：只能交给宿主自己的全选。
            if (!accepted) log("menu=false unreadable")
            return
        }
        if (text.isEmpty()) return
        if (after.coversAll(text.length)) {
            pending = Pending(connection, text, clock(), repaired = false)
            return
        }
        val repaired = setAll(connection, text.length)
        log("menu=$accepted after=${after.selectionStart},${after.selectionEnd}/${text.length} setSelection=$repaired")
        pending = Pending(connection, text, clock(), repaired = true)
    }

    /** onUpdateSelection 的回报：全选后不久宿主把选区收成了句尾光标，字没变，再补一次。 */
    fun selectionReported(connection: InputConnection, start: Int, end: Int) {
        val wait = pending ?: return
        if (wait.connection !== connection || clock() - wait.at > WINDOW_MS) {
            pending = null
            return
        }
        val length = wait.text.length
        if (minOf(start, end) == 0 && maxOf(start, end) == length) return // 全选本身的回报
        pending = null
        // 只认“收成句尾光标”：用户点到别处、另选一段、打了字都不管。
        if (start != end || start != length) return
        val now = read(connection) ?: return
        if (now.fullText() != wait.text || now.selectionStart != now.selectionEnd) return
        if (wait.repaired) {
            log("collapsed again to $start/$length after setSelection")
            return
        }
        val repaired = setAll(connection, length)
        log("collapsed to $start/$length by host, setSelection=$repaired")
    }

    /** 全选之后做了别的编辑、移了光标：不再补。 */
    fun cancel() {
        pending = null
    }

    private fun read(connection: InputConnection): ExtractedText? =
        runCatching { connection.getExtractedText(ExtractedTextRequest(), 0) }.getOrNull()

    private fun setAll(connection: InputConnection, length: Int): Boolean =
        runCatching { connection.setSelection(0, length) }.getOrDefault(false)

    /** 只有从头读到的才是全文；只给了一段（startOffset > 0）时不知道总长，不补。 */
    private fun ExtractedText.fullText(): String? = if (startOffset == 0) text?.toString() else null

    private fun ExtractedText.coversAll(length: Int): Boolean =
        minOf(selectionStart, selectionEnd) == 0 && maxOf(selectionStart, selectionEnd) == length

    companion object {
        /** 宿主收选区多在显示/隐藏浮动工具栏前后，几百毫秒内；过了这段时间的变化当成用户自己的操作。 */
        const val WINDOW_MS = 800L
    }
}
