package com.kingzcheung.xime.ocr

import android.content.Context
import android.util.AtomicFile
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.ModelManager
import java.io.File

/**
 * 拍照识别模型在哪：轻量版第一次用时下载（模型目录 [BuiltinModelCatalog.OCR_MODEL]），
 * 带模型的安装包（完整版、老人包、朋友包）照旧放在 assets/ocr/ppocr-v5。
 * 以前用过拍照识别的手机，安装包里的模型已经解到 noBackup/ocr-ppocr-v5-1，升级后接着用，不用重新下载。
 */
internal object OcrModelFiles {
    const val DETECTION = "ch_PP-OCRv5_det_mobile.onnx"
    const val RECOGNITION = "ch_PP-OCRv5_rec_mobile.onnx"
    const val CHARACTERS = "ppocrv5-characters.txt"
    private val names = listOf(DETECTION, RECOGNITION, CHARACTERS)
    private const val EXTRACTED_DIR = "ocr-ppocr-v5-1"
    private const val ASSET_DIR = "ocr/ppocr-v5"

    /** 目录里登记的大小：解出来的旧副本按它判断是否完整（写入是原子的，大小对就是完整的）。 */
    private fun expectedSizes(): Map<String, Long> =
        ModelManager.getModel(BuiltinModelCatalog.OCR_MODEL)?.resolvedVersion()?.files
            ?.associate { it.name to it.sizeBytes }.orEmpty()

    fun bundled(context: Context): Boolean = runCatching {
        context.assets.list(ASSET_DIR)?.toSet()?.containsAll(names) == true
    }.getOrDefault(false)

    private fun downloadedDir(context: Context): File? =
        if (ModelManager.isModelDownloaded(context, BuiltinModelCatalog.OCR_MODEL))
            ModelManager.getModelStorageDir(context, BuiltinModelCatalog.OCR_MODEL) else null

    private fun extractedDir(context: Context): File? {
        val dir = File(context.noBackupFilesDir, EXTRACTED_DIR)
        val sizes = expectedSizes()
        return dir.takeIf { names.all { name -> File(dir, name).length().let { it > 0 && (sizes[name] ?: it) == it } } }
    }

    /** 不解压、不联网，只看现在能不能识别。 */
    fun available(context: Context): Boolean =
        downloadedDir(context) != null || extractedDir(context) != null || bundled(context)

    /** 能用的模型文件夹；安装包带着时先解到 noBackup。都没有返回 null（该去下载）。 */
    fun prepare(context: Context): File? {
        downloadedDir(context)?.let { return it }
        extractedDir(context)?.let { return it }
        if (!bundled(context)) return null
        val dir = File(context.noBackupFilesDir, EXTRACTED_DIR).apply { check(mkdirs() || isDirectory) }
        for (name in names) {
            val target = File(dir, name)
            if (target.length() > 0L) continue
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                context.assets.open("$ASSET_DIR/$name").use { it.copyTo(stream) }
                atomic.finishWrite(stream)
            } catch (e: Exception) { atomic.failWrite(stream); throw e }
        }
        return dir
    }
}

/** 轻量版还没下载拍照识别模型。 */
internal class OcrModelMissingException : IllegalStateException("需要先下载拍照识别模型")
