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
        )
    }
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in setOf("ctrl_space", "shift_tap", "follow_cursor", "avoid_editor", "dock_edge",
            "page_minus_equals", "page_brackets", "page_comma_period", "bottom_bar"))
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(PREFIX + key, enabled).apply()
    }
}
