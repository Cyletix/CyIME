package com.kingzcheung.xime.ui.caption

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The separate home-screen "字幕" icon. Off unless the user turns it on (a second icon annoys
 * everyone else); an elder preset may switch it on by default. The quick-settings tile and the
 * keyboard menu open captions either way.
 */
object CaptionLauncherIcon {
    private fun component(context: Context) = ComponentName(context.packageName, "com.kingzcheung.xime.ui.caption.CaptionLauncher")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP)
    }
}
