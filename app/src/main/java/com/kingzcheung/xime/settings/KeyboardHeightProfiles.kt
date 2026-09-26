package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.ui.keyboard.floatingResizeHeightBounds
import com.kingzcheung.xime.ui.keyboard.keyboardHeightBounds

/** 固定键盘继续使用原有高度键；悬浮高度独立保存，横竖屏互不覆盖。 */
internal object KeyboardHeightProfiles {
    const val FLOATING_HEIGHT = "floating_height_dp"
    const val FLOATING_HEIGHT_LANDSCAPE = "floating_height_dp_landscape"

    private fun fixedKey(landscape: Boolean) =
        if (landscape) "keyboard_height_dp_landscape" else "keyboard_height_dp"

    private fun floatingKey(landscape: Boolean) =
        if (landscape) FLOATING_HEIGHT_LANDSCAPE else FLOATING_HEIGHT

    fun fixedBounds(context: Context, landscape: Boolean): IntRange =
        keyboardHeightBounds(context.resources.configuration.screenHeightDp, landscape)

    fun fixed(context: Context, landscape: Boolean): Int =
        SettingsPreferences.getKeyboardHeightDp(context, landscape)
            .coerceIn(fixedBounds(context, landscape))

    fun floating(context: Context, landscape: Boolean, hostHeightDp: Int): Int {
        val bounds = floatingResizeHeightBounds(hostHeightDp, landscape)
        val stored = SettingsPreferences.getPrefsPublic(context).getInt(floatingKey(landscape), 0)
        val height = stored.takeIf { it > 0 }
            ?: SettingsPreferences.getDefaultKeyboardHeightDp(context, landscape)
        return height.coerceIn(bounds)
    }

    fun selected(context: Context, floating: Boolean, landscape: Boolean, hostHeightDp: Int): Int =
        if (floating) floating(context, landscape, hostHeightDp) else fixed(context, landscape)

    /** 只有确认才调用。悬浮确认绝不写 keyboard_height_dp*。 */
    fun save(
        context: Context,
        floating: Boolean,
        landscape: Boolean,
        heightDp: Int,
        hostHeightDp: Int,
    ): Int {
        val height = if (floating) {
            heightDp.coerceIn(floatingResizeHeightBounds(hostHeightDp, landscape))
        } else {
            heightDp.coerceIn(fixedBounds(context, landscape))
        }
        if (floating) {
            SettingsPreferences.getPrefsPublic(context).edit()
                .putInt(floatingKey(landscape), height).apply()
        } else {
            SettingsPreferences.setKeyboardHeightDp(context, height, landscape)
        }
        return height
    }

    /**
     * 仅首次加载当前方向时修复旧版共用高度键造成的越界值。
     * 合法固定高度原样保留；低于固定下限的旧值先备份，再恢复固定默认高度。
     * 旧版已覆盖且仍在合法范围内的值无法可靠辨别，不擅自改写。
     */
    fun migrateLegacy(context: Context, landscape: Boolean, hostHeightDp: Int) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val suffix = if (landscape) "_landscape" else ""
        val marker = "height_profiles_v1$suffix"
        if (prefs.getBoolean(marker, false)) return

        val key = fixedKey(landscape)
        val raw = prefs.getInt(key, 0)
        val bounds = fixedBounds(context, landscape)
        val editor = prefs.edit().putBoolean(marker, true)
        val hasFloatingSettings = prefs.getBoolean("floating_mode$suffix", false) ||
            prefs.getInt("floating_width_dp$suffix", 0) > 0
        if (!prefs.contains(floatingKey(landscape)) && hasFloatingSettings && raw > 0) {
            editor.putInt(floatingKey(landscape), raw.coerceIn(floatingResizeHeightBounds(hostHeightDp, landscape)))
        }
        if (prefs.contains(key) && raw < bounds.first) {
            editor.putInt("${key}_before_height_profiles", raw)
            editor.putInt(key, SettingsPreferences.getDefaultKeyboardHeightDp(context, landscape).coerceIn(bounds))
        }
        // 同一 editor 批量写入，监听器不会读到“已打迁移标记但高度尚未修复”的半成品。
        editor.apply()
    }
}
