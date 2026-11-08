package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.ui.keyboard.HardwareToolbarPosition

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
    /** 浮条按钮：勾上 = 收起时隐藏；不勾 = 收起时也显示。展开时一律显示全部。 */
    val collapseKeyboard: Boolean = false,
    val collapseLanguage: Boolean = false,
    val collapseEmoji: Boolean = true,
    val collapseClipboard: Boolean = true,
    val collapseVoice: Boolean = true,
    val collapseSettings: Boolean = false,
    /** 闲置 3 秒自动半透明（浮条不再动时淡下去，触摸恢复）。 */
    val idleFade: Boolean = true,
    /** 浮条整体缩放：100% 为当前大小，最大 150%。 */
    val toolbarScale: Float = 1f,
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
            collapseKeyboard = p.getBoolean(PREFIX + "collapse_keyboard", false),
            collapseLanguage = p.getBoolean(PREFIX + "collapse_language", false),
            collapseEmoji = p.getBoolean(PREFIX + "collapse_emoji", true),
            collapseClipboard = p.getBoolean(PREFIX + "collapse_clipboard", true),
            collapseVoice = p.getBoolean(PREFIX + "collapse_voice", true),
            collapseSettings = p.getBoolean(PREFIX + "collapse_settings", false),
            idleFade = p.getBoolean(PREFIX + "idle_fade", true),
            toolbarScale = p.getFloat(PREFIX + "toolbar_scale", 1f).coerceIn(1f, 1.5f),
            associationInWindow = p.getBoolean(PREFIX + "association_in_window", false),
            gamePassthrough = p.getBoolean(PREFIX + "game_passthrough", false),
        )
    }
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in setOf("ctrl_space", "shift_tap", "follow_cursor", "avoid_editor", "dock_edge",
            "page_minus_equals", "page_brackets", "page_comma_period", "bottom_bar", "association_in_window",
            "game_passthrough", "candidate_arrows", "collapse_keyboard", "collapse_language", "collapse_emoji",
            "collapse_clipboard", "collapse_voice", "collapse_settings", "idle_fade"))
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(PREFIX + key, enabled).apply()
    }

    fun setFloat(context: Context, key: String, value: Float) {
        require(key in setOf("toolbar_scale"))
        SettingsPreferences.getPrefsPublic(context).edit().putFloat(PREFIX + key, value).apply()
    }
    /** 上次停放位置；没存过时返回 null，由调用方决定默认形态。 */
    internal fun readToolbarPosition(context: Context): HardwareToolbarPosition? {
        val p = SettingsPreferences.getPrefsPublic(context)
        val x = p.getFloat(PREFIX + "toolbar_x", Float.NaN)
        val y = p.getFloat(PREFIX + "toolbar_y", Float.NaN)
        return if (x.isFinite() && y.isFinite()) {
            HardwareToolbarPosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        } else null
    }

    internal fun setToolbarPosition(context: Context, position: HardwareToolbarPosition) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putFloat(PREFIX + "toolbar_x", position.xFraction)
            .putFloat(PREFIX + "toolbar_y", position.yFraction)
            .apply()
    }
}
