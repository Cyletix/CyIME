package com.kingzcheung.xime.service

import android.content.BroadcastReceiver
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.lang.ref.WeakReference

/**
 * 开发者工具（只给 [DevLearningReceiver] 用）：在当前输入框里试每一种回车做法，看聊天软件哪种算发送、哪种算换行。
 * 用户 11-04：QQ、微信按自己的「回车键发送」设置处理输入法发来的回车，普通回车和 Ctrl+回车结果一样。
 * 应用怎么分辨只能在真机上试，这里每种做法单独一个名字，脚本逐个发、看输入框变成什么样。
 *
 * 每次试：清空输入框，上屏一个标记（如 t01），等应用处理完，执行这一种做法，再等一下，量输入框。
 * 只报长度和最后一个字是不是换行，不报输入框里的文字。
 */
internal object EnterProbe {
    @Volatile var service: WeakReference<XimeInputMethodService>? = null

    val methods = listOf(
        "text_lf", "text_lf_prev", "compose_lf", "text_cr", "text_ls",
        "key_soft", "key_plain", "key_shift", "key_ctrl", "key_numpad",
        "action_send", "action_mask", "action_id", "action_unspecified", "action_done",
    )

    /** 当前输入框的 EditorInfo：包名、动作、标记、类型，不含提示文字和输入框内容。 */
    fun describe(info: EditorInfo?): String {
        info ?: return "editor: none"
        val options = info.imeOptions
        val action = when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> "unspecified"
            EditorInfo.IME_ACTION_NONE -> "none"
            EditorInfo.IME_ACTION_GO -> "go"
            EditorInfo.IME_ACTION_SEARCH -> "search"
            EditorInfo.IME_ACTION_SEND -> "send"
            EditorInfo.IME_ACTION_NEXT -> "next"
            EditorInfo.IME_ACTION_DONE -> "done"
            EditorInfo.IME_ACTION_PREVIOUS -> "previous"
            else -> "other"
        }
        val noEnter = options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multi = EnterKeyAction.isMultiLineText(info.inputType)
        return "editor: pkg=${info.packageName} imeOptions=0x${Integer.toHexString(options)} action=$action " +
            "noEnterAction=$noEnter inputType=0x${Integer.toHexString(info.inputType)} multiLine=$multi " +
            "actionId=${info.actionId} actionLabel=${info.actionLabel} private=${info.privateImeOptions} " +
            "extras=${info.extras?.keySet()?.sorted()}"
    }

    /** 清空、上屏 [marker]、执行 [method]，量完把结果交给广播。 */
    fun run(method: String, marker: String, pending: BroadcastReceiver.PendingResult) {
        fun finish(message: String) { android.util.Log.i("DevLearning", message); pending.resultData = message; pending.finish() }
        val ime = service?.get() ?: return finish("enter_probe: keyboard not running")
        if (method !in methods && method != CLEAR) return finish("enter_probe: unknown method $method, one of ${methods.joinToString(",")}")
        val ic = ime.currentInputConnection ?: return finish("enter_probe: no input field")
        val handler = ime.mainHandler
        ic.finishComposingText()
        ic.deleteSurroundingText(MAX, MAX)
        if (method == CLEAR) return finish("enter_probe: cleared")
        ic.commitText(marker, 1)
        handler.postDelayed({
            val before = length(ic)
            val returned = runCatching { perform(ime, ic, method) }.getOrElse { "error:${it.javaClass.simpleName}" }
            handler.postDelayed({
                // 发送后应用可能重启输入框，量新的连接。
                val now = ime.currentInputConnection ?: ic
                val after = length(now)
                val lf = now.getTextBeforeCursor(1, 0)?.toString() == "\n"
                val result = when {
                    after == 0 -> "sent"
                    after == before + 1 && lf -> "newline"
                    after == before -> "nothing"
                    else -> "other"
                }
                finish("enter_probe: method=$method result=$result returned=$returned before=$before after=$after lastIsNewline=$lf")
            }, SETTLE_MS)
        }, SETTLE_MS)
    }

    private fun length(ic: InputConnection): Int =
        (ic.getTextBeforeCursor(MAX, 0)?.length ?: 0) + (ic.getTextAfterCursor(MAX, 0)?.length ?: 0)

    private fun perform(ime: XimeInputMethodService, ic: InputConnection, method: String): String {
        val info = ime.currentInputEditorInfo
        return when (method) {
            "text_lf" -> ic.commitText("\n", 1).toString()
            // 把光标前一个字和换行一起上屏：应用只认「单独插进一个换行」时，这样不算回车。
            "text_lf_prev" -> {
                val two = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                val prev = when {
                    two.length == 2 && Character.isSurrogatePair(two[0], two[1]) -> two
                    two.isNotEmpty() -> two.takeLast(1)
                    else -> return "no-text-before-cursor"
                }
                ic.beginBatchEdit()
                ic.deleteSurroundingText(prev.length, 0)
                val ok = ic.commitText(prev + "\n", 1)
                ic.endBatchEdit()
                ok.toString()
            }
            "compose_lf" -> (ic.setComposingText("\n", 1) && ic.finishComposingText()).toString()
            "text_cr" -> ic.commitText("\r", 1).toString()
            "text_ls" -> ic.commitText(" ", 1).toString()
            "key_soft" -> { ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); "sent-keys" }
            // 不带 FLAG_SOFT_KEYBOARD，和实体键盘的回车一样。
            "key_plain" -> keys(ic, KeyEvent.KEYCODE_ENTER, 0, 0).toString()
            "key_shift" -> keys(ic, KeyEvent.KEYCODE_ENTER, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
                KeyEvent.KEYCODE_SHIFT_LEFT).toString()
            "key_ctrl" -> keys(ic, KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
                KeyEvent.KEYCODE_CTRL_LEFT).toString()
            "key_numpad" -> keys(ic, KeyEvent.KEYCODE_NUMPAD_ENTER, 0, 0, SOFT).toString()
            "action_send" -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND).toString()
            "action_mask" -> ic.performEditorAction((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION).toString()
            "action_id" -> info?.actionId?.takeIf { it != 0 }?.let { ic.performEditorAction(it).toString() } ?: "no-actionId"
            "action_unspecified" -> ic.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED).toString()
            "action_done" -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE).toString()
            else -> "unknown"
        }
    }

    /** [modifierKey] 不为 0 时先按下、最后松开这个修饰键，[meta] 一直带着。 */
    private fun keys(ic: InputConnection, keyCode: Int, meta: Int, modifierKey: Int, flags: Int = SOFT_IF_MODIFIER): Boolean {
        val now = SystemClock.uptimeMillis()
        val f = if (flags == SOFT_IF_MODIFIER) (if (modifierKey != 0) SOFT else 0) else flags
        fun event(action: Int, code: Int, metaState: Int) =
            KeyEvent(now, now, action, code, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, f)
        var ok = true
        if (modifierKey != 0) ok = ic.sendKeyEvent(event(KeyEvent.ACTION_DOWN, modifierKey, meta)) && ok
        ok = ic.sendKeyEvent(event(KeyEvent.ACTION_DOWN, keyCode, meta)) && ok
        ok = ic.sendKeyEvent(event(KeyEvent.ACTION_UP, keyCode, meta)) && ok
        if (modifierKey != 0) ok = ic.sendKeyEvent(event(KeyEvent.ACTION_UP, modifierKey, 0)) && ok
        return ok
    }

    /** 只清空输入框，脚本跑完收尾用。 */
    private const val CLEAR = "clear"
    private const val MAX = 4096
    private const val SETTLE_MS = 500L
    private const val SOFT = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
    private const val SOFT_IF_MODIFIER = -1
}
