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
    var selected by mutableStateOf(VisualStyle.ORIGINAL)
        private set
    var linked by mutableStateOf(true)
        private set
    val effective: VisualStyle get() = if (linked) VisualStyles.current else selected

    fun reload(context: Context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        linked = prefs.getBoolean(KEY_LINKED, true)
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
}

object LauncherIcons {
    fun component(context: Context, style: VisualStyle) = ComponentName(context.packageName,
        "com.kingzcheung.xime.launcher.Icon" + style.id.replaceFirstChar { it.uppercaseChar() })

    fun apply(context: Context, style: VisualStyle) {
        val pm = context.packageManager
        val target = component(context, style)
        val changes = VisualStyle.entries.map { candidate ->
            component(context, candidate) to if (candidate == style) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
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
