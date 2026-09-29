package com.kingzcheung.xime.plugin.core.config

import android.content.Context

/** One override source for settings and runtime. Keep the legacy file/key for upgrades. */
object PluginEnablementStore {
    private fun prefs(context: Context) = context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)
    fun isEnabled(context: Context, id: String, installedDefault: Boolean): Boolean =
        prefs(context).getBoolean("plugin_enabled_$id", installedDefault)
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("plugin_enabled_$id", enabled).apply()
    }
}
