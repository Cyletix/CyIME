package com.kingzcheung.xime.plugin.core.config

import android.content.Context

/** One override source for settings and runtime. Keep the legacy file/key for upgrades. */
object PluginEnablementStore {
    private fun prefs(context: Context) = context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)
    fun isEnabled(context: Context, id: String, installedDefault: Boolean): Boolean =
        prefs(context).getBoolean("plugin_enabled_$id", installedDefault)
    /** 用户自己拨过这个插件的开关（没拨过就是安装时的默认值）。 */
    fun hasChoice(context: Context, id: String): Boolean = prefs(context).contains("plugin_enabled_$id")
    /** 卸载时删掉开关记录：重新安装按新装处理，不带回旧的「已启用」。 */
    fun clear(context: Context, id: String) {
        prefs(context).edit().remove("plugin_enabled_$id").apply()
    }
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("plugin_enabled_$id", enabled).apply()
    }
}
