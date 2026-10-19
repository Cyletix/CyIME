package com.kingzcheung.xime.ui.caption

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.ThemeIconColors

/**
 * The separate home-screen "字幕" icon. Off unless the user turns it on (a second icon annoys
 * everyone else); an elder preset may switch it on by default. The quick-settings tile and the
 * keyboard menu open captions either way.
 *
 * A pinned shortcut recolours in place (updateShortcuts). Launchers that cannot pin, or a request
 * made in the background, fall back to a launcher alias; that alias comes in each theme colour and
 * is swapped on theme change (the launcher re-adds it, as with the main icon). A theme change never
 * turns the icon on when it is off.
 */
object CaptionLauncherIcon {
    private const val SHORTCUT_ID = "caption"
    private const val ALIAS = "com.kingzcheung.xime.ui.caption.CaptionLauncher"

    /** The legacy (fixed-colour) alias first, then one per theme colour. */
    private fun aliases(context: Context) = (listOf("") + ThemeIconColors.ids.map { c -> c.replaceFirstChar { it.uppercaseChar() } })
        .map { ComponentName(context.packageName, ALIAS + it) }

    private fun aliasFor(context: Context, themeId: String) = ComponentName(context.packageName,
        ALIAS + ThemeIconColors.forTheme(themeId).replaceFirstChar { it.uppercaseChar() })

    private fun aliasEnabled(context: Context) = aliases(context).any {
        context.packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    /** Enable only the alias in the given theme's colour, or none. */
    private fun setAlias(context: Context, enabled: Boolean, themeId: String = SettingsPreferences.getKeyboardTheme(context)) {
        val pm = context.packageManager
        val target = if (enabled) aliasFor(context, themeId) else null
        val changes = aliases(context).map { name ->
            name to if (name == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }.filter { (name, state) ->
            val current = pm.getComponentEnabledSetting(name)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else current != state
        }
        if (changes.isEmpty()) return
        // Same swap as the main icon (LauncherIcons.apply): one batch on Android 13+, so the launcher
        // treats it as a replacement; otherwise enable the new entry before disabling the old one.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (name, state) ->
                PackageManager.ComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            changes.sortedByDescending { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                .forEach { (name, state) -> pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP) }
        }
    }

    private fun pinned(context: Context) = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .firstOrNull { it.id == SHORTCUT_ID }
    }.getOrNull()

    fun isEnabled(context: Context): Boolean = aliasEnabled(context) || pinned(context)?.isEnabled == true

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!enabled) {
            setAlias(context, false)
            // A pinned icon cannot be removed by the app; disabling greys it out until the user removes it.
            runCatching { ShortcutManagerCompat.disableShortcuts(context, listOf(SHORTCUT_ID), "字幕图标已关闭，可长按移除") }
            return
        }
        val info = shortcut(context, SettingsPreferences.getKeyboardTheme(context))
        val existing = pinned(context)
        val requested = when {
            existing != null -> runCatching {
                ShortcutManagerCompat.enableShortcuts(context, listOf(info))
                ShortcutManagerCompat.updateShortcuts(context, listOf(info))
            }.isSuccess
            ShortcutManagerCompat.isRequestPinShortcutSupported(context) ->
                runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
            else -> false
        }
        if (requested) setAlias(context, false) else setAlias(context, true)
    }

    /** Recolour the icon after a theme change: the pinned shortcut in place, the alias by swapping. Off stays off. */
    fun refresh(context: Context, themeId: String) {
        if (aliasEnabled(context) && aliasFor(context, themeId).let {
                context.packageManager.getComponentEnabledSetting(it) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }) setAlias(context, true, themeId)
        if (pinned(context) != null) ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(context, themeId)))
    }

    private fun shortcut(context: Context, themeId: String) = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
        .setShortLabel("字幕")
        .setIcon(IconCompat.createWithResource(context, icon(ThemeIconColors.forTheme(themeId))))
        .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, CaptionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        .build()

    private fun icon(color: String): Int = when (color) {
        "softblue" -> R.mipmap.caption_launcher_softblue
        "sakura" -> R.mipmap.caption_launcher_sakura
        "sunset" -> R.mipmap.caption_launcher_sunset
        "aurora" -> R.mipmap.caption_launcher_aurora
        "mint" -> R.mipmap.caption_launcher_mint
        "cream" -> R.mipmap.caption_launcher_cream
        "desert" -> R.mipmap.caption_launcher_desert
        "mono" -> R.mipmap.caption_launcher_mono
        else -> R.mipmap.caption_launcher_lavender
    }
}
