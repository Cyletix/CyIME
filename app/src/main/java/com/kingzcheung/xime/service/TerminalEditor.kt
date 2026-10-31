package com.kingzcheung.xime.service

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 当前宿主输入框是不是终端（Termux、ConnectBot 等 SSH 客户端，判定见 [EditorInfoClassifier.isTerminalEditor]）。
 *
 * 终端只收按键和直接上屏的文字，读回来的光标前后文字永远是空的：
 * - 移光标、上下翻命令历史、Home/End 必须真的发方向键，不能先读文字再判断“到头了”；
 * - 拼音编码、语音临时结果不能写进组合区：按键式连接收到组合区文字就直接发给终端，之后每次改写都会重复一遍；
 * - 撤销不能发 Ctrl+Z，那会把终端前台程序（vim、top）挂起；粘贴宿主不接，要直接上屏剪贴板文字。
 *
 * 主线程在 onStartInput 写，按键线程和界面读。
 */
internal object TerminalEditor {
    @Volatile var active: Boolean = false
        internal set

    /** 「编码显示在输入框」在终端里改到候选栏显示；只影响这个输入框，用户的设置不变。 */
    fun inputTextLocation(context: Context): String =
        if (active) SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR else SettingsPreferences.getInputTextLocation(context)

    /** 发 [times] 次按下/抬起；每次都发出去才算一步。 */
    fun sendKeys(ic: InputConnection, keyCode: Int, times: Int = 1, onStep: () -> Unit = {}) {
        repeat(times.coerceIn(0, 1000)) {
            val down = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val up = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            if (!down && !up) return
            onStep()
        }
    }

    /** 实体键盘的方向键原样转给终端，保留 Shift/Ctrl/Alt，终端自己换成对应的转义序列。 */
    fun forward(ic: InputConnection, event: KeyEvent) {
        val down = if (event.action == KeyEvent.ACTION_DOWN) event else KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN)
        ic.sendKeyEvent(down)
        ic.sendKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
    }

    /** 编辑面板与手势里的光标动作在终端里对应的按键；选择类动作终端没有选区，按普通移动处理。 */
    fun keyForEditingAction(action: String): Int? = when (action) {
        "arrow_left", "select_arrow_left" -> KeyEvent.KEYCODE_DPAD_LEFT
        "arrow_right", "select_arrow_right" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "arrow_up", "select_arrow_up" -> KeyEvent.KEYCODE_DPAD_UP
        "arrow_down", "select_arrow_down" -> KeyEvent.KEYCODE_DPAD_DOWN
        "home", "line_start", "select_line_start", "select_paragraph_start" -> KeyEvent.KEYCODE_MOVE_HOME
        // select_end 是退出选择模式，不是“选到末尾”。
        "end", "line_end", "select_line_end", "select_paragraph_end" -> KeyEvent.KEYCODE_MOVE_END
        else -> null
    }
}
