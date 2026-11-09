package com.kingzcheung.xime.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 从本机导入模型：单个或多个模型文件、整合包（.zip）、或一个文件夹。
 * 不要求用户说明是哪个模型：按目录里登记的 sha256 自动认出文件属于哪个模型的哪个版本，
 * 认不出的文件不会被安装。官方归档（如 .tar.bz2）按归档哈希识别后解开；
 * 其他 .tar.bz2 / .tar.gz / .tar（如 sherpa-onnx 原版 Paraformer 包）逐个文件按哈希挑出认得的，其余不落盘。
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
                append("${unmatched.size} 个文件不属于任何已知模型(${unmatched.take(3).joinToString("、")}${if (unmatched.size > 3) " 等" else ""})")
            }
            if (isEmpty()) append("没有找到可导入的文件")
        }
    }

    /** 一个模型版本与它需要的本地文件（键为目录里登记的文件名）。 */
    internal data class Match(val model: ModelInfo, val version: ModelVersion, val files: Map<String, File>, val archive: File?)

    /**
     * 导入暂存区。散装文件先看大小：目录里没有这个大小的文件一定认不出，不再整份复制进缓存
     * （选 sherpa-onnx 解压后的整个文件夹时，fp32 原版就有八百多 MB，以前会先全部复制，空间不够就整次失败）。
     * 压缩包、大小未知的文件照旧复制；目录里有带哈希却没登记大小的文件时不按大小筛。
     */
    internal class Intake(val work: File, private val knownSizes: Set<Long>?) {
        val skipped = mutableListOf<String>()

        fun wants(name: String, size: Long): Boolean {
            if (knownSizes == null || size <= 0 || size in knownSizes || looksPacked(name)) return true
            skipped += name
            return false
        }

        private fun looksPacked(name: String): Boolean =
            listOf(".zip", ".tar", ".bz2", ".gz", ".tgz", ".tbz2").any { name.endsWith(it, ignoreCase = true) } || '.' !in name
    }

    /** 选中的若干文件；.zip 视为整合包展开。 */
    suspend fun importDocuments(context: Context, uris: List<Uri>): Report =
        importFrom(context) { intake ->
            uris.forEach { uri ->
                val (name, size) = nameAndSize(context, uri)
                if (intake.wants(name, size)) collect(context, uri, name, intake.work)
            }
        }

    /** 选中的文件夹（含子文件夹），即“自定义模型路径”。 */
    suspend fun importTree(context: Context, treeUri: Uri): Report =
        importFrom(context) { intake ->
            val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            walkTree(context, treeUri, DocumentsContract.getDocumentId(root)) { uri, name, size ->
                if (intake.wants(name, size)) collect(context, uri, name, intake.work)
            }
        }

    private suspend fun importFrom(context: Context, gather: (Intake) -> Unit): Report = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "model-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val models = ModelManager.getAllModels()
            val intake = Intake(work, knownLooseSizes(models))
            gather(intake)
            val hashes = HashMap<File, String>()
            val hash = { file: File -> hashes.getOrPut(file) { sha256(file) } }
            unpackArchives(work, models, hash)
            val files = work.walkTopDown().filter { it.isFile }.toList()
            val bySha = files.associateBy(hash)
            val matches = matchModels(models, bySha)
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
            Report(installed, intake.skipped + files.filter { it !in used }.map { it.name.substringAfter("__") }, failed)
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

    /** 目录里所有带哈希的单个文件的大小；有任何一个没登记大小时返回 null（不按大小筛）。 */
    internal fun knownLooseSizes(models: List<ModelInfo>): Set<Long>? {
        val files = models.flatMap { it.versions }.flatMap { it.files }.filter { it.sha256.isNotBlank() }
        return if (files.any { it.sizeBytes <= 0 }) null else files.map { it.sizeBytes }.toSet()
    }

    /**
     * 以前只有目录里登记了归档哈希的模型（X-ASR 等）能整包导入；Paraformer、SenseVoice 按单个文件登记，
     * 用户直接选 sherpa-onnx 官方的 .tar.bz2 时整包被当成一个认不出的文件。
     * 现在不是已登记归档的压缩包都解开：只留下大小和哈希都对得上目录里某个文件的条目（fp32 原版、测试音频等直接跳过），
     * 挑出了文件就丢掉这个压缩包；一个都没认出时留着它，报告里照常算作认不出的文件。
     */
    private fun unpackArchives(work: File, models: List<ModelInfo>, hash: (File) -> String) {
        val versions = models.flatMap { it.versions }
        val knownArchives = versions.filter { it.archiveUrl != null }.map { it.sha256.lowercase() }.filter { it.isNotBlank() }.toSet()
        val knownFiles = versions.flatMap { it.files }.filter { it.sha256.isNotBlank() && it.sizeBytes > 0 }
        val knownSizes = knownFiles.map { it.sizeBytes }.toSet()
        val knownShas = knownFiles.map { it.sha256.lowercase() }.toSet()
        work.listFiles().orEmpty().filter { it.isFile && archiveKind(it) != null }.forEach { archive ->
            if (hash(archive).lowercase() in knownArchives) return@forEach
            val kept = try {
                unpackKnownEntries(archive, work, { it in knownSizes }) { it in knownShas }
            } catch (e: Exception) {
                FileLogger.w(TAG, "Not a model archive: ${archive.name.substringAfter("__")}: ${e.message}")
                emptyList()
            }
            if (kept.isNotEmpty()) archive.delete()
        }
    }

    internal enum class ArchiveKind { TAR, TAR_BZ2, TAR_GZ }

    /** 按文件头认压缩包，不依赖文件名（文件选择器给的名字可能没有扩展名）。 */
    internal fun archiveKind(file: File): ArchiveKind? {
        val head = ByteArray(262)
        val read = file.inputStream().use { input ->
            var total = 0
            while (total < head.size) {
                val n = input.read(head, total, head.size - total)
                if (n < 0) break
                total += n
            }
            total
        }
        return when {
            read >= 3 && head[0] == 'B'.code.toByte() && head[1] == 'Z'.code.toByte() && head[2] == 'h'.code.toByte() -> ArchiveKind.TAR_BZ2
            read >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte() -> ArchiveKind.TAR_GZ
            read >= 262 && String(head, 257, 5, Charsets.US_ASCII) == "ustar" -> ArchiveKind.TAR
            else -> null
        }
    }

    /**
     * 解开一个 tar 包（可带 bzip2 / gzip），只把 [sizeWanted] 且解出后 [shaWanted] 的普通文件存进 [work]，
     * 名字沿用 [save] 的编号方式。条目一边写一边算哈希，对不上的立刻删掉。中途出错时删掉已解出的文件再抛出。
     */
    internal fun unpackKnownEntries(archive: File, work: File, sizeWanted: (Long) -> Boolean, shaWanted: (String) -> Boolean): List<File> {
        val kind = archiveKind(archive) ?: return emptyList()
        val kept = mutableListOf<File>()
        try {
            BufferedInputStream(archive.inputStream(), 64 * 1024).use { raw ->
                val decoded = when (kind) {
                    ArchiveKind.TAR_BZ2 -> BZip2CompressorInputStream(raw)
                    ArchiveKind.TAR_GZ -> GzipCompressorInputStream(raw)
                    ArchiveKind.TAR -> raw
                }
                TarArchiveInputStream(decoded).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        if (entry.isFile && sizeWanted(entry.size)) {
                            val safe = entry.name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
                            val target = File(work, "${UUID.randomUUID()}__$safe")
                            val digest = MessageDigest.getInstance("SHA-256")
                            try {
                                target.outputStream().use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    var n = tar.read(buffer)
                                    while (n >= 0) {
                                        if (n > 0) { digest.update(buffer, 0, n); out.write(buffer, 0, n) }
                                        n = tar.read(buffer)
                                    }
                                }
                            } catch (e: Exception) {
                                target.delete()
                                throw e
                            }
                            val sha = digest.digest().joinToString("") { "%02x".format(it) }
                            if (shaWanted(sha)) kept += target else target.delete()
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            kept.forEach { it.delete() }
            throw e
        }
        return kept
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

    private fun walkTree(context: Context, treeUri: Uri, documentId: String, onFile: (Uri, String, Long) -> Unit) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: id
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(context, treeUri, id, onFile)
                } else {
                    val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
                    onFile(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name, size)
                }
            }
        }
    }

    /** 文件名与大小；大小未知时为 -1（不按大小筛）。 */
    private fun nameAndSize(context: Context, uri: Uri): Pair<String, Long> =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) (it.getString(0) ?: uri.lastPathSegment.orEmpty()) to (if (it.isNull(1)) -1L else it.getLong(1))
            else null
        } ?: (uri.lastPathSegment.orEmpty() to -1L)

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
