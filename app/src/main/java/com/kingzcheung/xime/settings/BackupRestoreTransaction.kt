package com.kingzcheung.xime.settings

import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

internal interface BackupPreferenceStore {
    fun validate(json: ByteArray)
    fun snapshot(name: String): ByteArray
    fun restore(name: String, json: ByteArray, replace: Boolean = false)
}

/** Validate the complete archive, then commit files and preferences with rollback. */
internal object BackupRestoreTransaction {
    @Synchronized
    fun restore(filesDir: File, preferences: BackupPreferenceStore, bytes: ByteArray): Result<Unit> {
        val staging = File(filesDir, ".restore-${UUID.randomUUID()}")
        val changes = mutableListOf<Pair<File, File>>()
        val oldFiles = linkedMapOf<File, File?>()
        val settings = linkedMapOf<String, ByteArray>()
        val oldSettings = linkedMapOf<String, ByteArray>()
        var preserveRecovery = false
        fun target(base: File, relative: String): File {
            require(relative.isNotBlank() && !relative.startsWith("/") && !relative.contains('\\') &&
                !Regex("^[A-Za-z]:").containsMatchIn(relative) && relative.split('/').none { it == ".." }) {
                "备份包包含非法路径: $relative"
            }
            val file = File(base, relative).canonicalFile
            require(file.path.startsWith(base.canonicalPath + File.separator)) { "备份包路径越界: $relative" }
            return file
        }
        try {
            check(staging.mkdirs()) { "无法创建恢复暂存目录" }
            var count = 0
            val seen = hashSetOf<String>()
            ZipInputStream(bytes.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory) { input.closeEntry(); continue }
                    val name = entry.name
                    require(seen.add(name)) { "备份包包含重复条目: $name" }
                    val rel = name.removePrefix(BackupManager.META_PREFIX)
                    val metadata = name.startsWith(BackupManager.META_PREFIX)
                    val prefsName = when {
                        metadata && rel == "settings.json" -> "kime_settings"
                        metadata && rel.startsWith("plugin_configs/") && rel.endsWith(".json") -> {
                            val id = rel.removePrefix("plugin_configs/").removeSuffix(".json")
                            require(id.isNotBlank() && !id.contains('/') && !id.contains('\\') && id != "..") {
                                "插件配置标识无效"
                            }
                            "plugin_cfg_$id"
                        }
                        metadata && rel.startsWith("prefs/") && rel.endsWith(".json") ->
                            rel.removePrefix("prefs/").removeSuffix(".json")
                                .takeIf { it in BackupManager.SETTINGS_PREFS || it in BackupManager.PARTIAL_PREFS }
                        else -> null
                    }
                    if (prefsName != null) {
                        val data = input.readBytes()
                        preferences.validate(data)
                        settings[prefsName] = data
                    } else {
                        val destination = when {
                            !metadata -> target(File(filesDir, "rime"), name)
                            rel == "plugins.xml" -> File(filesDir, "plugins.xml")
                            rel.startsWith("plugins/") -> target(File(filesDir, "plugins"), rel.removePrefix("plugins/"))
                            rel.startsWith("files/") -> rel.removePrefix("files/").let { path ->
                                require(BackupManager.isFilesEntry(path, personal = true)) { "备份包包含未知文件: $path" }
                                target(filesDir, path)
                            }
                            else -> null
                        }
                        if (destination != null) {
                            require(changes.none { it.first == destination }) { "备份包目标重复" }
                            val staged = File(staging, "new-$count")
                            staged.outputStream().use { input.copyTo(it) }
                            changes += destination to staged
                        }
                    }
                    count++
                    input.closeEntry()
                }
            }
            require(count > 0) { "备份包为空" }
            settings.keys.forEach { oldSettings[it] = preferences.snapshot(it) }
            changes.forEachIndexed { index, (destination, _) ->
                require(!destination.exists() || destination.isFile) { "恢复目标不是文件: ${destination.name}" }
                oldFiles[destination] = if (destination.exists()) File(staging, "old-$index").also {
                    destination.copyTo(it)
                } else null
            }
            try {
                changes.forEach { (destination, staged) ->
                    check(destination.parentFile.isDirectory || destination.parentFile.mkdirs()) { "无法创建恢复目录" }
                    staged.copyTo(destination, overwrite = true)
                }
                settings.forEach { (name, data) -> preferences.restore(name, data) }
            } catch (failure: Exception) {
                oldFiles.entries.toList().asReversed().forEach { (destination, old) ->
                    try {
                        if (old == null) { if (destination.isFile) check(destination.delete()) }
                        else old.copyTo(destination, overwrite = true)
                    } catch (rollback: Exception) { failure.addSuppressed(rollback); preserveRecovery = true }
                }
                oldSettings.forEach { (name, data) ->
                    try { preferences.restore(name, data, replace = true) }
                    catch (rollback: Exception) { failure.addSuppressed(rollback); preserveRecovery = true }
                }
                throw failure
            }
            return Result.success(Unit)
        } catch (failure: Exception) {
            return Result.failure(failure)
        } finally {
            // Only this transaction's unique, app-private directory may be deleted.
            if (!preserveRecovery) staging.deleteRecursively()
        }
    }
}
