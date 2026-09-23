package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File
import java.io.IOException

/** 日语词库与上游默认方案子模块独立分发，兼容用户从市场更新同名 jaroomaji 文件。 */
object JapaneseSchemas {
    val ids = listOf("japanese", "japanese_kana")
    private const val ASSET_DIRECTORY = "rime_japanese"
    private const val ADDED = "japanese_schemas_added_v1"

    @Synchronized
    fun installAssets(context: Context, target: File) {
        target.mkdirs()
        for (name in context.assets.list(ASSET_DIRECTORY).orEmpty()) {
            if (!name.endsWith(".yaml")) continue
            val destination = File(target, name)
            if (destination.exists()) continue
            val temporary = File(target, "$name.installing")
            try {
                context.assets.open("$ASSET_DIRECTORY/$name").use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                }
                if (!temporary.renameTo(destination)) throw IOException("无法安装日语方案 $name")
            } finally {
                temporary.delete()
            }
        }
    }

    /** 升级时仅追加本轮新方案一次，不重新启用用户已禁用的其它内置方案。 */
    fun addOnFirstUpgrade(context: Context, enabled: List<String>): List<String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(ADDED, false)) return enabled
        val updated = addMissing(enabled)
        if (updated != enabled) SchemaManager.setEnabledSchemas(context, updated)
        prefs.edit().putBoolean(ADDED, true).apply()
        return updated
    }

    internal fun addMissing(enabled: List<String>): List<String> = enabled + ids.filterNot { it in enabled }
}
