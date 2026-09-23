package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File
import java.io.IOException

/** 全拼与合并键共用内置拼音词典；14 键独立随主应用分发，不覆盖市场同名方案。 */
object ChineseSchemas {
    val ids = listOf("pinyin_simp", "pinyin_14jian")
    private const val ADDED = "chinese_keyboards_added_v1"

    @Synchronized
    fun installAssets(context: Context, target: File) {
        target.mkdirs()
        val name = "pinyin_14jian.schema.yaml"
        val destination = File(target, name)
        if (destination.exists()) return
        val temporary = File(target, "$name.installing")
        try {
            context.assets.open("rime_chinese/$name").use { input ->
                temporary.outputStream().use { input.copyTo(it) }
            }
            if (!temporary.renameTo(destination)) throw IOException("无法安装中文14键方案")
        } finally { temporary.delete() }
    }

    /** 更新时仅补齐一次；之后在方案管理里关闭仍然有效。 */
    @Synchronized
    fun addOnFirstUpgrade(context: Context, enabled: List<String>): List<String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(ADDED, false)) return enabled
        val updated = enabled + ids.filterNot { it in enabled }
        if (updated != enabled) SchemaManager.setEnabledSchemas(context, updated)
        prefs.edit().putBoolean(ADDED, true).apply()
        return updated
    }

    fun displayName(id: String, original: String): String = when (id) {
        "pinyin_simp" -> "中文26键"
        "pinyin_14jian" -> "中文14键"
        else -> original
    }
}
