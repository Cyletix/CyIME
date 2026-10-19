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
 * It is a pinned shortcut, so its colour follows the keyboard theme in place (updateShortcuts),
 * without the launcher removing and re-adding it. The old launcher alias remains only as a
 * fallback for launchers that cannot pin shortcuts or when pinning is refused in the background.
 */
object CaptionLauncherIcon {
    private const val SHORTCUT_ID = "caption"

    private fun alias(context: Context) = ComponentName(context.packageName, "com.kingzcheung.xime.ui.caption.CaptionLauncher")

    private fun aliasEnabled(context: Context) =
        context.packageManager.getComponentEnabledSetting(alias(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun setAlias(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(alias(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP)
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

    /** Recolour an existing pinned icon after a theme change; does nothing when none is pinned. */
    fun refresh(context: Context, themeId: String) {
        if (pinned(context) == null) return
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(context, themeId)))
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
