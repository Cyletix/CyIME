package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.plugin.core.runtime.installer.InstallerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一的导入入口。
 *
 * 设计原则：**路由永远按「文件名 / 内容类型」集中在这里决定**，而不是由调用方传入的
 * "方向"参数决定。这样无论是文件选择器还是分享，都汇聚到
 * 同一个路由核心，结构上不可能出现各入口路由逻辑不同步的问题。
 *
 * 调用方（各页面）只需要关心它从哪个入口触发导入，并对返回的 [ImportResult] 给出对应的
 * 成功文案与刷新动作即可。
 */
object ImportManager {

    sealed class ImportResult {
        /**
         * 方案 / 词典 / 背景图等，走 [SchemaManager.saveImportedFile] 的落地结果。
         * 方案压缩包导入后直接安装；和已有文件同名时不覆盖，[pendingPackageId] 留给页面问用户。
         * [message] 是给用户看的一句结果。
         */
        data class Content(
            val success: Boolean,
            val installedDirect: Boolean,
            val pendingPackageId: String? = null,
            val pendingName: String = "",
            val message: String = if (success) "导入成功" else "导入失败",
        ) : ImportResult()

        /** 插件（.xipk）安装结果。 */
        data class Plugin(val pluginInfo: PluginInfo?) : ImportResult()

        data class Failed(val reason: String) : ImportResult()
        data class Unsupported(val fileName: String) : ImportResult()
    }

    /** 判断文件名是否为插件包（仅 .xipk）。 */
    fun isPluginFile(name: String): Boolean =
        name.endsWith(".xipk", ignoreCase = true)

    /** 从 URI 导入（文件选择器、分享等）。按文件名自动路由。 */
    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri)
            ?: return@withContext ImportResult.Failed("无法识别文件名")
        val name = SchemaManager.sanitizeDisplayName(displayName)

        if (isPluginFile(name)) {
            when (val r = PluginManager.installerManager.installPluginFromUri(uri)) {
                is InstallerManager.InstallResult.Success -> ImportResult.Plugin(r.pluginInfo)
                is InstallerManager.InstallResult.Failure -> ImportResult.Failed(r.reason)
            }
        } else {
            val stream = when (uri.scheme) {
                "file" -> java.io.FileInputStream(uri.path!!)
                else -> context.contentResolver.openInputStream(uri)
            }
            if (stream == null) {
                ImportResult.Failed("无法读取文件")
            } else {
                val r = SchemaManager.saveImportedFile(context, name, stream)
                val packageId = r.packageId
                if (r.success && packageId != null) installArchive(context, packageId, archiveTitle(name))
                else ImportResult.Content(r.success, r.installedDirect,
                    message = if (r.success && r.installedDirect) "已导入，点「应用方案更改」生效" else if (r.success) "导入成功" else "导入失败")
            }
        }
    }

    /** 导入的方案包直接安装（并打开其中的主方案）；有同名文件时留着等确认，装不上就删掉存下的包。 */
    private suspend fun installArchive(context: Context, packageId: String, title: String): ImportResult.Content {
        val install = SchemaManager.installPackageFromMarketDir(context, packageId, displayName = title)
        return when {
            install.success -> ImportResult.Content(true, installedDirect = true, message = buildString {
                append("已安装「$title」，点「应用方案更改」生效")
                if (install.parseFailures.isNotEmpty()) append("\n" + install.parseFailures.joinToString("\n"))
            })
            install.conflicts.isNotEmpty() -> ImportResult.Content(true, installedDirect = false,
                pendingPackageId = packageId, pendingName = title,
                message = "「$title」里有和已装方案同名的文件，确认后再安装")
            else -> {
                runCatching { SchemaManager.getMarketDir(context, packageId).deleteRecursively() }
                ImportResult.Content(false, installedDirect = false,
                    message = "导入失败：${install.failureReason ?: "安装失败"}")
            }
        }
    }

    /** 压缩包名去掉扩展名，作为方案包的显示名。 */
    internal fun archiveTitle(name: String): String =
        Regex("""\.(zip|tar\.gz|tgz)$""", RegexOption.IGNORE_CASE).replace(name, "").ifBlank { name }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }
}
