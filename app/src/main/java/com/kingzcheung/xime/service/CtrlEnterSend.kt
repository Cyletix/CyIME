package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection

/** Ctrl+回车补发回车键时，比较执行「发送」前后输入框的样子（见 ImeKeyRouter.sendWithFallback）。只在内存里比，不记文字。 */
internal object CtrlEnterSend {
    /** 应用发出后清空输入框：QQ、TIM、微信 0.5 秒内都已清空（11-04 试验，0.5 秒时量的）。 */
    const val SETTLE_MS = 500L
    private const val MAX = 4096

    data class Snapshot(val beforeCursor: String, val afterCursor: String) {
        val isEmpty: Boolean get() = beforeCursor.isEmpty() && afterCursor.isEmpty()
    }

    enum class Outcome {
        /** 和执行前一模一样：应用没理。 */
        UNCHANGED,
        /** 只在光标前多了一个换行：应用把回车键当换行。 */
        NEWLINE_ADDED,
        /** 别的变化（清空 = 发出去了，或者用户接着打了字）：不再动。 */
        CHANGED,
    }

    fun snapshot(ic: InputConnection): Snapshot? {
        val before = ic.getTextBeforeCursor(MAX, 0) ?: return null
        val after = ic.getTextAfterCursor(MAX, 0) ?: return null
        return Snapshot(before.toString(), after.toString())
    }

    fun outcome(before: Snapshot, now: Snapshot?): Outcome = when {
        now == null -> Outcome.CHANGED
        now == before -> Outcome.UNCHANGED
        now.afterCursor == before.afterCursor && now.beforeCursor == before.beforeCursor + "\n" -> Outcome.NEWLINE_ADDED
        // 光标前已经满 MAX 个字时，多出的换行把最前面一个字挤出了读取范围。
        before.beforeCursor.length == MAX && now.afterCursor == before.afterCursor &&
            now.beforeCursor == before.beforeCursor.drop(1) + "\n" -> Outcome.NEWLINE_ADDED
        else -> Outcome.CHANGED
    }
}
