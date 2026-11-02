package com.kingzcheung.xime.ocr

import android.content.Context
import android.util.AtomicFile
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.ModelManager
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 拍照识别模型在哪：轻量版第一次用时下载（模型目录 [BuiltinModelCatalog.OCR_MODEL]），
 * 带模型的安装包（完整版、老人包、朋友包）照旧放在 assets/ocr/ppocr-v5。
 * 安装包把两个模型不压缩存放（app/build.gradle.kts 的 noCompress），识别时直接映射安装包里的那段，不再解出第二份；
 * 以前解到 noBackup/ocr-ppocr-v5-1 的旧副本，能直接映射后在启动时删掉（[obsoleteExtractedDir]）。
 * 安装包里的模型被压缩了（没带 noCompress 的旧打包方式）才照旧解压。
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

    /** 识别要用的模型：已下载或旧解压的目录，或者直接映射的安装包内容。 */
    sealed interface Source {
        class Directory(val dir: File) : Source
        /** 映射在安装包上的两个模型，识别会话存在期间要一直引用着。 */
        class Packaged(val detection: MappedByteBuffer, val recognition: MappedByteBuffer, val characters: List<String>) : Source
    }

    /** 安装包里的模型是不压缩存放的，能直接映射；压缩了 openFd 会失败，返回 null。 */
    private fun map(context: Context, name: String): MappedByteBuffer? = runCatching {
        context.assets.openFd("$ASSET_DIR/$name").use { fd ->
            // 映射建立后与文件描述符无关，关掉 fd 不影响；通道不单独关，fd 归 AssetFileDescriptor 管。
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }.getOrNull()

    private fun packaged(context: Context): Source.Packaged? {
        if (!bundled(context)) return null
        val detection = map(context, DETECTION) ?: return null
        val recognition = map(context, RECOGNITION) ?: return null
        val characters = context.assets.open("$ASSET_DIR/$CHARACTERS").bufferedReader(Charsets.UTF_8).use { it.readLines() }
        return Source.Packaged(detection, recognition, characters)
    }

    /** 安装包里的模型能直接映射时，以前解出来的副本就没用了，返回它好在启动时删掉；否则 null。 */
    fun obsoleteExtractedDir(context: Context): File? {
        val dir = File(context.noBackupFilesDir, EXTRACTED_DIR)
        if (!dir.exists() || !bundled(context)) return null
        return dir.takeIf { map(context, DETECTION) != null && map(context, RECOGNITION) != null }
    }

    /** 不解压、不联网，只看现在能不能识别。 */
    fun available(context: Context): Boolean =
        downloadedDir(context) != null || extractedDir(context) != null || bundled(context)

    /** 能用的模型：下载的优先，其次直接映射安装包；安装包里是压缩的才解到 noBackup。都没有返回 null（该去下载）。 */
    fun prepare(context: Context): Source? {
        downloadedDir(context)?.let { return Source.Directory(it) }
        packaged(context)?.let { return it }
        extractedDir(context)?.let { return Source.Directory(it) }
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
        return Source.Directory(dir)
    }
}

/** 轻量版还没下载拍照识别模型。 */
internal class OcrModelMissingException : IllegalStateException("需要先下载拍照识别模型")
