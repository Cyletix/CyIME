package com.kingzcheung.xime.service

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo

/**
 * 宿主输入连接外面包的一层，所有调用照原样转给宿主，只有一处不同：
 * 问光标前文字（getTextBeforeCursor，不带样式）时，[memory] 里有确定的答案就直接给，不再同步等宿主。
 *
 * 为此每个会改宿主文字的调用都先记进 [memory]：commitText(…, 1) 接到已知文字后面，其余一律让它作废。
 * 带 TextAttribute 的重载、replaceText、手写手势本输入法不调用，未单独处理（会原样转给宿主，不更新 [memory]）。
 */
internal class TrackedInputConnection(internal val base: InputConnection) : InputConnectionWrapper(base, false) {
    internal val memory = EditorTextMemory()

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        if (flags != 0 || n < 0) return super.getTextBeforeCursor(n, flags)
        memory.textBefore(n)?.let { return it }
        // 多要一点，后面要得更长（联想 25 字、九键 16 字）时也能直接回答；给调用方的仍是它要的长度。
        val requested = maxOf(n, EditorTextMemory.CAPACITY)
        val read = super.getTextBeforeCursor(requested, flags)
        memory.learn(read, requested)
        return if (read == null || read.length <= n) read else read.subSequence(read.length - n, read.length)
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        super.commitText(text, newCursorPosition).also { memory.committed(text, newCursorPosition, it) }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        super.setComposingText(text, newCursorPosition).also { memory.composingSet(text, it) }

    override fun finishComposingText(): Boolean {
        memory.composingFinished()
        return super.finishComposingText()
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        memory.composingRegion(start, end)
        return super.setComposingRegion(start, end)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        memory.edited()
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        memory.edited()
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        memory.edited()
        return super.sendKeyEvent(event)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        memory.edited()
        return super.setSelection(start, end)
    }

    override fun performContextMenuAction(id: Int): Boolean {
        memory.edited()
        return super.performContextMenuAction(id)
    }

    override fun performEditorAction(editorAction: Int): Boolean {
        memory.edited()
        return super.performEditorAction(editorAction)
    }

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
        memory.edited()
        return super.performPrivateCommand(action, data)
    }

    override fun commitCompletion(text: CompletionInfo?): Boolean {
        memory.edited()
        return super.commitCompletion(text)
    }

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
        memory.edited()
        return super.commitCorrection(correctionInfo)
    }

    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
        memory.edited()
        // 授权图片读取时框架拿 currentInputConnection 和发起调用的原连接比身份，对不上就不授权。
        return withRawConnection { super.commitContent(inputContentInfo, flags, opts) }
    }

    override fun closeConnection() {
        memory.edited()
        super.closeConnection()
    }

    companion object {
        private val raw = ThreadLocal<Boolean>()

        /** 本线程正在 [withRawConnection] 里：currentInputConnection 应给宿主的原连接。 */
        val wantsRaw: Boolean get() = raw.get() == true

        /**
         * [block] 里 currentInputConnection 给宿主的原连接，不给包装：框架会拿它比对身份（图片授权），
         * 或者把它存成当前连接（配置变化后重启输入，之后的 invalidateInput 只认原连接）。
         */
        fun <T> withRawConnection(block: () -> T): T {
            val previous = raw.get()
            raw.set(true)
            try { return block() } finally { raw.set(previous) }
        }
    }
}
