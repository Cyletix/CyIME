package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * 启动时清掉上次进程被杀（覆盖安装、系统回收）时没来得及删的临时文件。
 * 这些文件只在操作正常结束时删除：下载到一半的模型、导入和方案安装的暂存、
 * 模型替换时挪开的旧目录、发布到 rime/ 途中的链接，一个就可能几百 MB。
 */
object StorageHousekeeping {
    private const val TAG = "StorageHousekeeping"
    private const val UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    /** ModelDownloader / ModelImporter / SchemaManager 的暂存，以及归档模型下载中的 tar.bz2。 */
    private val CACHE_TEMP = Regex("^(?:model-download-$UUID|model-import-$UUID|schema-install-$UUID|-?\\d+\\.tar\\.bz2)$")
    /** installDownloadedModel 替换时挪开的旧模型目录。 */
    private val MODEL_PREVIOUS = Regex("^\\.(.+)-$UUID\\.previous$")
    /** installCandidateModel 发布前的临时链接；源文件仍在 models/，删掉不丢数据。 */
    private val RUNTIME_INSTALLING = Regex("^\\..+\\.$UUID\\.installing$")

    /**
     * 只收集调用时已经存在的条目，所以必须在本进程开始任何下载、导入或安装之前调用；
     * 之后新建的临时文件名字带新的 UUID，不会被这次清理碰到。
     */
    fun findLeftovers(cacheDir: File, modelsDir: File, rimeDir: File): List<File> =
        cacheDir.listFiles { file -> CACHE_TEMP.matches(file.name) }.orEmpty().toList() +
            // 文字识别请求的截图，识别完即删；进程被杀时留下。
            File(cacheDir, "ocr-frames").listFiles { file -> file.isFile }.orEmpty() +
            modelsDir.listFiles { file -> MODEL_PREVIOUS.matches(file.name) }.orEmpty() +
            rimeDir.listFiles { file -> file.isFile && RUNTIME_INSTALLING.matches(file.name) }.orEmpty()

    /** 返回腾出的字节数。被挪开的旧模型若原位置已空（替换中途被杀），放回原处而不是删除。 */
    fun removeLeftovers(files: List<File>): Long {
        var freed = 0L
        for (file in files) {
            val original = MODEL_PREVIOUS.matchEntire(file.name)?.let { File(file.parentFile, it.groupValues[1]) }
            if (original != null && !original.exists()) {
                if (!file.renameTo(original)) FileLogger.w(TAG, "无法放回被挪开的模型 ${file.name}")
                continue
            }
            val size = StorageStats.directorySize(file)
            if (file.deleteRecursively()) freed += size
        }
        return freed
    }

    /** 主进程启动时调用：同步列出残留（很快），后台删除。 */
    fun sweepAtStartup(context: Context, scope: CoroutineScope) {
        val leftovers = runCatching {
            findLeftovers(context.cacheDir, StorageStats.modelsDir(context), StorageStats.rimeDir(context))
        }.getOrDefault(emptyList())
        if (leftovers.isEmpty()) return
        scope.launch {
            runCatching { removeLeftovers(leftovers) }
                .onSuccess { FileLogger.i(TAG, "清掉上次中断留下的临时文件 ${leftovers.size} 项，共 ${it / 1024 / 1024} MB") }
                .onFailure { FileLogger.w(TAG, "清理临时文件失败：${it.message}") }
        }
    }
}
