package com.kingzcheung.xime.speech

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 中文 Zipformer（流式 zipformer-large）冷加载慢：编码器有 1.2 万个节点，sherpa-onnx 每次加载都要重新做图优化。
 * 2026-10-30 sherpa-onnx 1.13.8、x86 单线程实测：原文件 4.2–5.2 s，Paraformer 约 1 s。第一次用完后在后台用同一个
 * ONNX Runtime 做一遍 basic 级优化（常量折叠、删冗余节点，只产出标准算子，与硬件无关）另存一份，以后冷加载 2.6–2.7 s，
 * 识别结果逐字相同。副本放在模型目录外，不改变模型目录（引擎的复用键），已常驻的模型不会因此重载；
 * 源文件大小或修改时间一变就不用旧副本。每个源文件只试一次，转换中进程被杀也不会反复重试。
 */
internal object OptimizedEncoder {
    private const val TAG = "OptimizedEncoder"
    private const val NAME = "encoder.basic.onnx"
    private const val STAMP = "encoder.basic.stamp"
    private const val ATTEMPT = "encoder.basic.attempt"
    /** 转换时 :asr 进程会多占约一份模型的内存，小内存机器不做。 */
    private const val MIN_TOTAL_MEMORY = 6L * 1024 * 1024 * 1024
    private val running = AtomicBoolean(false)

    private fun cacheDir(context: Context, modelId: String) = File(File(context.noBackupFilesDir, "asr-optimized"), modelId)
    private fun stamp(source: File) = "${source.name}:${source.length()}:${source.lastModified()}"

    /** 属于这个源文件的优化副本；没有或已过期时为 null。 */
    fun path(context: Context, modelDir: File, encoderFile: String): File? {
        val cache = cacheDir(context, modelDir.name)
        val optimized = File(cache, NAME)
        val stampFile = File(cache, STAMP)
        if (!optimized.isFile || optimized.length() == 0L || !stampFile.isFile) return null
        return optimized.takeIf { runCatching { stampFile.readText() }.getOrNull() == stamp(File(modelDir, encoderFile)) }
    }

    /** sherpa-onnx 读副本失败时调用：删掉副本，回到原文件，也不再重试。 */
    fun reject(context: Context, modelId: String) {
        val cache = cacheDir(context, modelId)
        File(cache, NAME).delete(); File(cache, STAMP).delete()
    }

    /** 模型被删除时一起删掉副本。 */
    fun forget(context: Context, modelId: String) { cacheDir(context, modelId).deleteRecursively() }

    /** 一次语音输入结束后调用：中文流式 Zipformer 还没有副本就在低优先级线程里做一份。 */
    fun prepareLater(context: Context, selection: AsrModelManager.Selection) {
        val first = selection.first ?: return
        val dir = selection.firstDir ?: return
        if (first.modelType != "transducer" || SpeechModelCatalog.isJapaneseZipformer(first.id)) return
        val source = File(dir, first.encoderFile)
        if (!source.isFile || path(context, dir, first.encoderFile) != null) return
        val memory = context.getSystemService(ActivityManager::class.java)?.let { am ->
            if (am.isLowRamDevice) 0L else ActivityManager.MemoryInfo().also(am::getMemoryInfo).totalMem
        } ?: 0L
        if (memory < MIN_TOTAL_MEMORY) return
        val cache = cacheDir(context, dir.name)
        val expected = stamp(source)
        val attempt = File(cache, ATTEMPT)
        if (runCatching { attempt.readText() }.getOrNull() == expected) return
        if (!running.compareAndSet(false, true)) return
        Thread({
            val tmp = File(cache, "$NAME.tmp")
            try {
                cache.mkdirs()
                attempt.writeText(expected)
                tmp.delete()
                val startedAt = System.nanoTime()
                val env = OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options ->
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    options.setOptimizedModelFilePath(tmp.absolutePath)
                    options.setIntraOpNumThreads(1)
                    env.createSession(source.absolutePath, options).close()
                }
                // The copy must load in this same runtime (sherpa-onnx shares libonnxruntime.so) before it is ever used.
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(1)
                    env.createSession(tmp.absolutePath, options).close()
                }
                check(tmp.length() > 0 && tmp.renameTo(File(cache, NAME))) { "副本写入失败" }
                File(cache, STAMP).writeText(expected)
                FileLogger.w(TAG, "optimized ${dir.name} encoder in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
            } catch (e: Throwable) {
                tmp.delete()
                FileLogger.w(TAG, "encoder optimization skipped for ${dir.name}: ${e.message}")
            } finally { running.set(false) }
        }, "CyIME-AsrOptimize").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}
