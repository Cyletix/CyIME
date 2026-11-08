package com.kingzcheung.xime.settings

import android.content.Context

data class HardwareKeyboardOptions(
    val ctrlSpace: Boolean = true,
    val shiftTap: Boolean = true,
    val followCursor: Boolean = true,
    val avoidEditor: Boolean = true,
    val dockAtEdge: Boolean = true,
    val pageMinusEquals: Boolean = true,
    val pageBrackets: Boolean = true,
    val pageCommaPeriod: Boolean = true,
    /** 吸附到底边时使用三星式贴底通栏（整宽、九个带编号的候选）；关闭时保持原来的底部长条。 */
    val bottomBar: Boolean = false,
    /** 候选条两端的翻页箭头；关闭时仍可用方向键和「候选翻页」里配置的按键翻页。默认关。 */
    val candidateArrows: Boolean = false,
    /** 浮条上显示哪些按钮；展开键盘和语言键始终保留。 */
    val toolbarEmoji: Boolean = true,
    val toolbarClipboard: Boolean = true,
    val toolbarVoice: Boolean = true,
    val toolbarSettings: Boolean = true,
    /** 联想词也显示在跟随光标的候选浮窗里（和打字候选同一个位置）；关闭时联想在工具条上。默认关。 */
    val associationInWindow: Boolean = false,
    /** 游戏直通：游戏类应用里不显示工具条，实体按键直接交给游戏。默认关。 */
    val gamePassthrough: Boolean = false,
)

object HardwareKeyboardPreferences {
    const val PREFIX = "hardware_keyboard_"
    fun read(context: Context): HardwareKeyboardOptions {
        val p = SettingsPreferences.getPrefsPublic(context)
        return HardwareKeyboardOptions(
            ctrlSpace = p.getBoolean(PREFIX + "ctrl_space", true),
            shiftTap = p.getBoolean(PREFIX + "shift_tap", true),
            followCursor = p.getBoolean(PREFIX + "follow_cursor", true),
            avoidEditor = p.getBoolean(PREFIX + "avoid_editor", true),
            dockAtEdge = p.getBoolean(PREFIX + "dock_edge", true),
            pageMinusEquals = p.getBoolean(PREFIX + "page_minus_equals", true),
            pageBrackets = p.getBoolean(PREFIX + "page_brackets", true),
            pageCommaPeriod = p.getBoolean(PREFIX + "page_comma_period", true),
            bottomBar = p.getBoolean(PREFIX + "bottom_bar", false),
            candidateArrows = p.getBoolean(PREFIX + "candidate_arrows", false),
            toolbarEmoji = p.getBoolean(PREFIX + "toolbar_emoji", true),
            toolbarClipboard = p.getBoolean(PREFIX + "toolbar_clipboard", true),
            toolbarVoice = p.getBoolean(PREFIX + "toolbar_voice", true),
            toolbarSettings = p.getBoolean(PREFIX + "toolbar_settings", true),
            associationInWindow = p.getBoolean(PREFIX + "association_in_window", false),
            gamePassthrough = p.getBoolean(PREFIX + "game_passthrough", false),
        )
    }
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in setOf("ctrl_space", "shift_tap", "follow_cursor", "avoid_editor", "dock_edge",
            "page_minus_equals", "page_brackets", "page_comma_period", "bottom_bar", "association_in_window",
            "game_passthrough", "candidate_arrows", "toolbar_emoji", "toolbar_clipboard", "toolbar_voice",
            "toolbar_settings"))
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(PREFIX + key, enabled).apply()
    }
}
