package com.kingzcheung.xime.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.settings.SettingsPreferences

object IconAppearance {
    const val KEY_STYLE = "cyime_icon_style"
    const val KEY_LINKED = "cyime_icon_linked"
    const val KEY_FRAMED = "cyime_icon_framed"
    const val KEY_FOLLOW_THEME = "cyime_icon_follow_theme"
    var framed by mutableStateOf(true)
        private set
    /** 桌面图标改用键盘主题色的折面图标（带底板）；关闭时回到所选样式。 */
    var followTheme by mutableStateOf(false)
        private set
    var selected by mutableStateOf(VisualStyle.ORIGINAL)
        private set
    var linked by mutableStateOf(true)
        private set
    val effective: VisualStyle get() = if (linked) VisualStyles.current else selected

    fun reload(context: Context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        linked = prefs.getBoolean(KEY_LINKED, true)
        framed = prefs.getBoolean(KEY_FRAMED, true)
        followTheme = prefs.getBoolean(KEY_FOLLOW_THEME, false)
        selected = VisualStyle.fromId(prefs.getString(KEY_STYLE, SettingsPreferences.getVisualStyle(context).id))
    }

    fun setKeyboardStyle(context: Context, style: VisualStyle) {
        reload(context)
        if (linked) LauncherIcons.apply(context, style)
        val editor = SettingsPreferences.getPrefsPublic(context).edit().putString(SettingsPreferences.KEY_VISUAL_STYLE, style.id)
        if (linked) { editor.putString(KEY_STYLE, style.id); selected = style }
        VisualStyles.current = style
        editor.apply()
    }

    fun setIconStyle(context: Context, style: VisualStyle) {
        reload(context)
        if (linked) setKeyboardStyle(context, style) else {
            LauncherIcons.apply(context, style)
            selected = style
            SettingsPreferences.getPrefsPublic(context).edit().putString(KEY_STYLE, style.id).apply()
        }
    }

    fun setLinked(context: Context, value: Boolean) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        // Enabling the binding adopts the visible keyboard style; disabling keeps the visible pair.
        val icon = if (value) SettingsPreferences.getVisualStyle(context) else effective
        LauncherIcons.apply(context, icon)
        selected = icon
        linked = value
        prefs.edit().putBoolean(KEY_LINKED, value).putString(KEY_STYLE, icon.id).apply()
    }

    fun setFramed(context: Context, value: Boolean) {
        reload(context)
        LauncherIcons.apply(context, effective, value)
        framed = value
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_FRAMED, value).apply()
    }

    fun setFollowTheme(context: Context, value: Boolean) {
        reload(context)
        followTheme = value
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_FOLLOW_THEME, value).apply()
        LauncherIcons.apply(context, effective)
    }

    /** 换键盘主题后：跟随主题时换桌面图标；字幕快捷方式的颜色总是跟随（原地更新，不会跳位）。 */
    fun onKeyboardThemeChanged(context: Context, themeId: String) {
        reload(context)
        if (followTheme) runCatching { LauncherIcons.apply(context, effective, themeId = themeId) }
        runCatching { com.kingzcheung.xime.ui.caption.CaptionLauncherIcon.refresh(context, themeId) }
    }
}

/** 键盘主题 → 桌面图标配色（与 IconLab designs/cyime-caption.json 的配色同名）。 */
object ThemeIconColors {
    val ids = listOf("lavender", "softblue", "sakura", "sunset", "aurora", "mint", "cream", "desert", "mono")

    fun forTheme(themeId: String): String = when (themeId) {
        SoftBlueTheme.ID -> "softblue"
        PureBlackTheme.ID, TransparentGlassTheme.ID -> "mono"
        "sakura_pink" -> "sakura"
        "sunset_gradient" -> "sunset"
        "aurora_gradient" -> "aurora"
        "mint_green" -> "mint"
        "cream_beige" -> "cream"
        "olivcelso_desert" -> "desert"
        else -> "lavender"
    }
}

object LauncherIcons {
    fun component(context: Context, style: VisualStyle, framed: Boolean = IconAppearance.framed) = ComponentName(context.packageName,
        "com.kingzcheung.xime.launcher.Icon" + style.id.replaceFirstChar { it.uppercaseChar() } + if (framed) "" else "Bare")

    fun themeComponent(context: Context, color: String) = ComponentName(context.packageName,
        "com.kingzcheung.xime.launcher.IconTheme" + color.replaceFirstChar { it.uppercaseChar() })

    fun apply(context: Context, style: VisualStyle, framed: Boolean = IconAppearance.framed,
              themeId: String = com.kingzcheung.xime.settings.SettingsPreferences.getKeyboardTheme(context)) {
        val pm = context.packageManager
        val target = if (IconAppearance.followTheme) themeComponent(context, ThemeIconColors.forTheme(themeId))
            else component(context, style, framed)
        val candidates = VisualStyle.entries.flatMap { candidate -> listOf(true, false).map { component(context, candidate, it) } } +
            ThemeIconColors.ids.map { themeComponent(context, it) }
        val changes = candidates.map { name ->
            name to if (name == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.filter { (name, state) -> pm.getComponentEnabledSetting(name) != state }
        if (changes.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (name, state) ->
                PackageManager.ComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Never disable the last launcher entry before its replacement is available.
            pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            changes.filter { it.first != target }.forEach { (name, state) ->
                pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
