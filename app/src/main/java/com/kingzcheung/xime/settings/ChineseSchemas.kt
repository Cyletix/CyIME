package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/** 统一中文词库；标准 Rime ID 不变，现有市场版本和个人补丁优先。 */
object ChineseSchemas {
    val ids = listOf("t9_pinyin", "rime_ice", "pinyin_14jian", "double_pinyin_flypy")
    private const val ADDED = "cyime_chinese_defaults_v1"

    private const val MAX_MIGRATION_FILE_BYTES = 256 * 1024L

    /** 大词库只补缺失文件；启动时不解压、更不读入内存比较已有词库。 */
    internal fun isSmallMigrationCandidate(relative: String, size: Long): Boolean =
        size <= MAX_MIGRATION_FILE_BYTES &&
            (relative.endsWith(".schema.yaml") || relative.endsWith(".lua") ||
                relative in setOf("symbols_v.yaml", "symbols_caps_v.yaml"))

    private fun readSmallAsset(context: Context, path: String): ByteArray? = runCatching {
        context.assets.open(path).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_MIGRATION_FILE_BYTES) return@use null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }.getOrNull()

    @Synchronized
    fun installAssets(context: Context, target: File) {
        target.mkdirs()
        val registryFile = SchemaManifestManager.getRegistryFile(context)
        val registry = if (registryFile.isFile) runCatching { JSONObject(registryFile.readText()) }.getOrNull() else null
        fun isMarketOwned(relative: String): Boolean {
            // 已登记的市场/个人文件优先，即使它碰巧与旧内置版本内容一致也不替换。
            val owners = registry?.optJSONObject("files")?.optJSONObject(relative)?.optJSONArray("claimedBy") ?: return false
            return (0 until owners.length()).any { owners.optString(it) != "builtin" }
        }
        fun install(directory: String, relative: String = "") {
            val path = if (relative.isEmpty()) directory else "$directory/$relative"
            val children = context.assets.list(path).orEmpty()
            if (children.isNotEmpty()) {
                children.forEach { install(directory, if (relative.isEmpty()) it else "$relative/$it") }
                return
            }
            // 许可随 APK 分发，不作为 Rime 配置安装。
            if (relative.endsWith(".md") || relative == "LICENSE") return
            val destination = File(target, relative)
            var replacement: ByteArray? = null
            if (destination.exists()) {
                if (!destination.isFile || !isSmallMigrationCandidate(relative, destination.length()) || isMarketOwned(relative)) return
                val bytes = readSmallAsset(context, path) ?: return
                val current = destination.readBytes()
                if (current.contentEquals(bytes)) return
                val oldBuiltin = readSmallAsset(context, "rime/$relative")
                val old14 = if (relative == "pinyin_14jian.schema.yaml")
                    bytes.toString(Charsets.UTF_8).replace("dictionary: rime_ice", "dictionary: pinyin_simp").toByteArray() else null
                // 仅迁移字节完全匹配的旧内置小配置，不以方案名判断用户文件。
                if (oldBuiltin?.contentEquals(current) != true && old14?.contentEquals(current) != true) return
                replacement = bytes
            }
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.installing")
            try {
                temporary.outputStream().use { output ->
                    val bytes = replacement
                    if (bytes != null) output.write(bytes)
                    else context.assets.open(path).use { input -> input.copyTo(output) }
                }
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                if (replacement != null) SchemaManifestManager.recordBuiltinReplacement(context, relative, destination)
            } finally { temporary.delete() }
        }
        install("rime_ice")
        install("rime_chinese")
    }

    /** 一次整理旧默认和重复模式，之后用户的启停选择继续有效。 */
    @Synchronized
    fun addOnFirstUpgrade(context: Context, enabled: List<String>): List<String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val available = SchemaManager.getRimeDir(context).listFiles().orEmpty()
            .filter { it.name.endsWith(".schema.yaml") }.map { it.name.removeSuffix(".schema.yaml") }.toSet()
        val normalized = CyimeInputDefaults.canonicalIds(enabled, available)
        if (prefs.getBoolean(ADDED, false)) return normalized
        val updated = (normalized.filterNot { it in CyimeInputDefaults.legacyDefaults } + ids.filter { it in available }).distinct()
        if (updated != enabled) SchemaManager.setEnabledSchemas(context, updated)
        val current = SettingsPreferences.getCurrentSchema(context)
        val selected = CyimeInputDefaults.canonicalIds(listOf(current), available).firstOrNull()
        if (selected == null || selected !in updated) SettingsPreferences.setCurrentSchema(context, updated.firstOrNull() ?: "t9_pinyin")
        else if (selected != current) SettingsPreferences.setCurrentSchema(context, selected)
        prefs.edit().putBoolean(ADDED, true).apply()
        return updated
    }

    fun displayName(id: String, original: String): String = when (id) {
        "rime_ice" -> "中文26键"
        "pinyin_simp" -> "旧版简体拼音"
        "t9_pinyin", "t9" -> "中文九键"
        "pinyin_14jian" -> "中文14键"
        else -> original
    }
}
