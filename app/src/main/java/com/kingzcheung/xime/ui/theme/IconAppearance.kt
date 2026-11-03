package com.kingzcheung.xime.ui.theme

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
        // 图标可以是玻璃、毛玻璃；键盘材质只画还在提供的三种。
        VisualStyles.current = VisualStyle.forKeyboard(style)
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

    /**
     * 桌面图标三选一（用户 11-01）：只改和原来不同的那个值，少触发一次桌面刷新。
     * 原来两个开关的值照旧存着，见 [DesktopIconChoice.stored]。
     */
    fun setChoice(context: Context, choice: DesktopIconChoice) {
        reload(context)
        val (follow, link) = choice.stored(linked)
        if (link != linked) setLinked(context, link)
        if (follow != followTheme) setFollowTheme(context, follow)
    }

    /** 换键盘主题后：跟随主题时换桌面图标；字幕快捷方式的颜色总是跟随（原地更新，不会跳位）。 */
    fun onKeyboardThemeChanged(context: Context, themeId: String) {
        reload(context)
        // 字幕图标先换：三星换主图标入口时可能直接结束本应用进程，排在后面的更新会丢。
        runCatching { com.kingzcheung.xime.ui.caption.CaptionLauncherIcon.refresh(context, themeId) }
        if (followTheme) runCatching { LauncherIcons.apply(context, effective, themeId = themeId) }
    }
}

/**
 * 桌面图标三选一（用户 11-01：「同步图标与视觉样式」「桌面图标跟随主题色」两个开关合成一个选择）。
 * 存的仍是原来两个值，老用户不用迁移：跟随主题色优先（原来它开着时另一个不起作用）。
 */
enum class DesktopIconChoice(val label: String) {
    THEME("跟随主题色"), VISUAL_STYLE("跟随视觉样式"), CUSTOM("自己选");

    /** 写回（跟随主题色，同步视觉样式）；选跟随主题色时不动「同步」，切回来还是原来那种。 */
    fun stored(linked: Boolean): Pair<Boolean, Boolean> = when (this) {
        THEME -> true to linked
        VISUAL_STYLE -> false to true
        CUSTOM -> false to false
    }

    companion object {
        fun of(followTheme: Boolean, linked: Boolean): DesktopIconChoice = when {
            followTheme -> THEME
            linked -> VISUAL_STYLE
            else -> CUSTOM
        }
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
        CustomImageTheme.ID -> forTheme(CustomImageTheme.baseThemeId)
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
        val disabled = changes.filter { it.second == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }.mapTo(HashSet()) { it.first }
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
        reopenIfHosted(context, disabled, target)
    }

    /**
     * 设置页正开在刚关掉的桌面入口上时，系统随后会结束它，设置页约 1 秒后退回桌面（GitHub #120）。
     * 先用新入口重新打开「主题与外观」，下面照旧是设置首页；不是从桌面图标进来的设置页不受影响。
     */
    private fun reopenIfHosted(context: Context, disabled: Set<ComponentName>, target: ComponentName) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull() ?: return
        if (activity.componentName !in disabled || activity.isFinishing) return
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(target)
            .putExtra("open_fragment", com.kingzcheung.xime.ui.settings.SettingsRoutes.Theme)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { activity.startActivity(intent) }.recoverCatching {
            // 个别系统刚启用的入口还解析不到：直接开设置页本身。
            activity.startActivity(intent.setComponent(ComponentName(activity, "com.kingzcheung.xime.MainActivity")))
        }.onSuccess {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }.onFailure { com.kingzcheung.xime.util.FileLogger.e("LauncherIcons", "Reopen settings failed", it) }
    }
}
