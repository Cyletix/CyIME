package com.kingzcheung.xime.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 从本机导入模型：单个或多个模型文件、整合包（.zip）、或一个文件夹。
 * 不要求用户说明是哪个模型：按目录里登记的 sha256 自动认出文件属于哪个模型的哪个版本，
 * 认不出的文件不会被安装。官方归档（如 .tar.bz2）按归档哈希识别后解开。
 */
object ModelImporter {
    private const val TAG = "ModelImporter"

    data class Report(val installed: List<String>, val unmatched: List<String>, val failed: List<String>) {
        fun message(): String = buildString {
            if (installed.isNotEmpty()) append("已导入：${installed.joinToString("、")}")
            if (failed.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("导入失败：${failed.joinToString("、")}")
            }
            if (unmatched.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("${unmatched.size} 个文件不属于任何已知模型（${unmatched.take(3).joinToString("、")}${if (unmatched.size > 3) " 等" else ""}）")
            }
            if (isEmpty()) append("没有找到可导入的文件")
        }
    }

    /** 一个模型版本与它需要的本地文件（键为目录里登记的文件名）。 */
    internal data class Match(val model: ModelInfo, val version: ModelVersion, val files: Map<String, File>, val archive: File?)

    /** 选中的若干文件；.zip 视为整合包展开。 */
    suspend fun importDocuments(context: Context, uris: List<Uri>): Report =
        importFrom(context) { work -> uris.forEach { collect(context, it, displayName(context, it), work) } }

    /** 选中的文件夹（含子文件夹），即“自定义模型路径”。 */
    suspend fun importTree(context: Context, treeUri: Uri): Report =
        importFrom(context) { work ->
            val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            walkTree(context, treeUri, DocumentsContract.getDocumentId(root)) { uri, name -> collect(context, uri, name, work) }
        }

    private suspend fun importFrom(context: Context, gather: (File) -> Unit): Report = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "model-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            gather(work)
            val files = work.walkTopDown().filter { it.isFile }.toList()
            val bySha = files.associateBy { sha256(it) }
            val matches = matchModels(ModelManager.getAllModels(), bySha)
            val used = matches.flatMap { it.files.values + listOfNotNull(it.archive) }.toSet()
            val installed = mutableListOf<String>()
            val failed = mutableListOf<String>()
            for (match in matches) {
                try {
                    ModelManager.installLocal(context, match.model, match.version) { staging ->
                        if (match.archive != null) {
                            ModelDownloader.extractTarBz2(match.archive, staging)
                        } else {
                            match.files.forEach { (name, file) ->
                                val target = modelDownloadFile(staging, name)
                                target.parentFile?.mkdirs()
                                file.copyTo(target, overwrite = true)
                            }
                        }
                    }
                    installed += match.model.name
                } catch (e: Exception) {
                    FileLogger.e(TAG, "Import failed: ${match.model.id}: ${e.message}", e)
                    failed += match.model.name
                }
            }
            Report(installed, files.filter { it !in used }.map { it.name.substringAfter("__") }, failed)
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * 每个模型取第一个能凑齐的版本：有归档哈希的认归档，否则要求清单里每个文件都有 sha256 且都找到。
     * 没有 sha256 的条目无法确认内容，不参与本机导入。
     */
    internal fun matchModels(models: List<ModelInfo>, bySha: Map<String, File>): List<Match> {
        val lower = bySha.mapKeys { it.key.lowercase() }
        return models.mapNotNull { model ->
            model.versions.firstNotNullOfOrNull { version ->
                val archiveSha = version.sha256.lowercase()
                if (version.archiveUrl != null) {
                    lower[archiveSha]?.takeIf { archiveSha.isNotBlank() }?.let { Match(model, version, emptyMap(), it) }
                } else if (version.files.isNotEmpty() && version.files.all { it.sha256.isNotBlank() }) {
                    val found = version.files.associate { it.name to lower[it.sha256.lowercase()] }
                    if (found.values.all { it != null }) Match(model, version, found.mapValues { it.value!! }, null) else null
                } else null
            }
        }
    }

    private fun collect(context: Context, uri: Uri, name: String, work: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (name.endsWith(".zip", ignoreCase = true)) unzip(input, work) else save(input, name, work)
        } ?: throw IOException("无法读取 $name")
    }

    private fun unzip(input: InputStream, work: File) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) save(zip, entry.name.substringAfterLast('/'), work)
                entry = zip.nextEntry
            }
        }
    }

    /** 每个文件单独编号保存，同名文件（不同模型都有 tokens.txt）互不覆盖。 */
    private fun save(input: InputStream, name: String, work: File) {
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        val target = File(work, "${UUID.randomUUID()}__$safe")
        target.outputStream().use { input.copyTo(it) }
    }

    private fun walkTree(context: Context, treeUri: Uri, documentId: String, onFile: (Uri, String) -> Unit) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: id
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(context, treeUri, id, onFile)
                } else {
                    onFile(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name)
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
