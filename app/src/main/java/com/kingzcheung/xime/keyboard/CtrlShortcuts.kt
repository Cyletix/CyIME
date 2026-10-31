package com.kingzcheung.xime.keyboard

import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * 按住 26 键的符号键当 Ctrl 用（用户 10-24）：字母键上显示常用快捷键，拖到字母上松手发 Ctrl+字母。
 * 没列出的字母照样能发，只是键面不写用途。
 */
object CtrlShortcuts {
    /** 键面写的用途，两个字以内，手机上的窄键也放得下。 */
    private val labels = mapOf(
        'a' to "全选", 'c' to "复制", 'v' to "粘贴", 'x' to "剪切", 'z' to "撤销", 'y' to "重做",
        's' to "保存", 'f' to "查找", 'w' to "关闭", 't' to "新页", 'n' to "新建", 'o' to "打开",
        'r' to "刷新", 'p' to "打印", 'b' to "加粗", 'i' to "斜体",
    )

    fun label(letter: Char): String? = labels[letter.lowercaseChar()]

    /** 和实体键盘一样：Ctrl 按下、字母按下抬起都带 Ctrl、Ctrl 抬起；由应用自己处理（复制、关标签页等）。 */
    fun keyEvents(letter: Char, now: Long): List<KeyEvent> {
        val lower = letter.lowercaseChar()
        require(lower in 'a'..'z')
        val code = KeyEvent.KEYCODE_A + (lower - 'a')
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        fun event(action: Int, keyCode: Int, metaState: Int) =
            KeyEvent(now, now, action, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags)
        return listOf(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, meta),
            event(KeyEvent.ACTION_DOWN, code, meta),
            event(KeyEvent.ACTION_UP, code, meta),
            event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0),
        )
    }

    /** 「Z X C V 上角标改成撤销/剪切/复制/粘贴」打开时换掉上角标的几个字母（10-31 用户反馈，默认关）。 */
    val EDIT_CORNER_LETTERS = setOf('z', 'x', 'c', 'v')

    /** 单字母 z/x/c/v 键在开关打开时返回它的字母（上角标改发 Ctrl+这个字母），其他键和双字母键返回 null。 */
    fun editCornerLetter(key: String, enabled: Boolean): Char? =
        key.singleOrNull()?.lowercaseChar()?.takeIf { enabled && it in EDIT_CORNER_LETTERS }

    /** 键盘层经 [GestureAction.COMMAND] 传给服务的命令名。 */
    fun command(letter: Char): String = "ctrl:${letter.lowercaseChar()}"
    fun letterOf(command: String): Char? =
        command.removePrefix("ctrl:").takeIf { command.startsWith("ctrl:") && it.length == 1 && it[0] in 'a'..'z' }?.get(0)
}
