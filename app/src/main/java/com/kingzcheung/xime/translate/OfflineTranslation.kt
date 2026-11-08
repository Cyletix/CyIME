package com.kingzcheung.xime.translate

import android.content.Context
import com.kingzcheung.xime.model.ModelDownloadState
import com.kingzcheung.xime.model.ModelManager
import com.kingzcheung.xime.plugin.core.lua.OfflineTranslateHostApi
import com.kingzcheung.xime.plugin.core.lua.OfflineTranslateListener
import com.kingzcheung.xime.plugin.core.lua.OfflineTranslateStatus
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 离线翻译：Mozilla Firefox 翻译模型（Firefox Translations / Bergamot 项目训练，int8 量化），
 * 手机上用 slimt 引擎（libcytranslate.so）翻，不联网。每个方向一个模型，在「扩展 › 模型」下载。
 *
 * 模型条目（文件、哈希、下载地址）在 BuiltinModelCatalog（translate-zh-en、translate-en-zh）。
 */
object OfflineTranslation {
    private const val TAG = "OfflineTranslation"

    /** 一个方向：语言代码、模型目录 id、文件名。zh-en 两边共用一个词表。 */
    internal data class Direction(
        val from: String, val to: String, val modelId: String,
        val model: String, val sourceVocabulary: String, val targetVocabulary: String, val shortlist: String,
    )

    internal val directions = listOf(
        Direction("zh", "en", "translate-zh-en", "model.zhen.intgemm.alphas.bin",
            "vocab.zhen.spm", "vocab.zhen.spm", "lex.50.50.zhen.s2t.bin"),
        Direction("en", "zh", "translate-en-zh", "model.enzh.intgemm.alphas.bin",
            "srcvocab.enzh.spm", "trgvocab.enzh.spm", "lex.50.50.enzh.s2t.bin"),
    )

    internal fun direction(from: String, to: String): Direction? =
        directions.firstOrNull { it.from == from.lowercase() && it.to == to.lowercase() }

    /** 用完不关会一直占着内存：最后一次翻译后这么久没再用就关掉引擎（模型文件映射一起释放）。 */
    private const val IDLE_CLOSE_MS = 60_000L

    /** 一个线程按先后翻；同一时间只开一个方向的引擎。 */
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "offline-translate").apply { isDaemon = true } }
    private var engineDirection: Direction? = null
    private var engineHandle = 0L
    private var idleClose: ScheduledFuture<*>? = null

    /** 只在 [worker] 线程上调用。 */
    private fun engineFor(context: Context, direction: Direction): Long {
        if (engineHandle != 0L && engineDirection == direction) return engineHandle
        closeEngine()
        val dir = ModelManager.getModelStorageDir(context, direction.modelId) ?: error("模型目录不存在")
        fun path(name: String) = File(dir, name).absolutePath
        val started = System.nanoTime()
        engineHandle = OfflineTranslatorNative.nativeCreate(path(direction.model), path(direction.sourceVocabulary),
            path(direction.targetVocabulary), path(direction.shortlist))
        engineDirection = direction
        FileLogger.i(TAG, "加载 ${direction.modelId}：${(System.nanoTime() - started) / 1_000_000} ms")
        return engineHandle
    }

    private fun closeEngine() {
        if (engineHandle != 0L) OfflineTranslatorNative.nativeDestroy(engineHandle)
        engineHandle = 0L
        engineDirection = null
    }

    fun status(context: Context, from: String, to: String): OfflineTranslateStatus {
        val direction = direction(from, to) ?: return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_UNSUPPORTED)
        val model = ModelManager.getModel(direction.modelId) ?: return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_UNSUPPORTED)
        val sizeMb = ((model.files.sumOf { it.sizeBytes } + 512 * 1024) / (1024 * 1024)).toInt()
        if (!OfflineTranslatorNative.load()) return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_UNSUPPORTED)
        if (ModelManager.isModelDownloaded(context, model)) return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_READY, sizeMb = sizeMb)
        val downloading = ModelManager.downloadStates.value[model.id] as? ModelDownloadState.Downloading
        if (downloading != null) {
            return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_DOWNLOADING,
                (downloading.progress * 100).toInt().coerceIn(0, 100), sizeMb)
        }
        return OfflineTranslateStatus(OfflineTranslateHostApi.STATE_MISSING, sizeMb = sizeMb)
    }

    fun download(context: Context, from: String, to: String): String? {
        val direction = direction(from, to) ?: return "离线翻译没有这个方向"
        if (!OfflineTranslatorNative.load()) return "这台手机不支持离线翻译"
        val model = ModelManager.getModel(direction.modelId) ?: return "离线翻译没有这个方向"
        if (ModelManager.isModelDownloaded(context, model)) return null
        ModelManager.downloadModelInBackground(context, model)
        return null
    }

    private val nextId = AtomicInteger(0)
    @Volatile private var newestId = -1
    /** 插件只会取消自己最新的那一句（旧的已经被新的一句顶掉），记一个就够。 */
    @Volatile private var cancelledId = -1

    fun translate(context: Context, text: String, from: String, to: String, listener: OfflineTranslateListener): Int {
        val direction = direction(from, to)
        val failure = when {
            direction == null -> "离线翻译没有这个方向"
            !OfflineTranslatorNative.load() -> "这台手机不支持离线翻译"
            !ModelManager.isModelDownloaded(context, direction.modelId) -> "离线翻译模型还没下载"
            else -> null
        }
        if (failure != null) {
            listener.onError(failure)
            return -1
        }
        val id = nextId.getAndIncrement()
        newestId = id
        val app = context.applicationContext
        worker.execute {
            // 翻译栏只要最后一句：排队时又来了新的一句，这句不翻了
            if (id != newestId || id == cancelledId) return@execute
            idleClose?.cancel(false)
            val result = runCatching {
                val handle = engineFor(app, direction!!)
                OfflineTranslatorNative.nativeTranslate(handle, text)
            }
            idleClose = worker.schedule({ closeEngine() }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
            if (id == cancelledId) return@execute
            result.onSuccess { listener.onDone(it) }.onFailure { error ->
                FileLogger.w(TAG, "离线翻译失败", error)
                // 引擎出错就整个关掉，下次重开（模型文件被删、被换掉时）
                closeEngine()
                listener.onError("离线翻译出错：${error.message ?: error.javaClass.simpleName}")
            }
        }
        return id
    }

    fun cancel(id: Int) {
        if (id >= 0) cancelledId = id
    }
}

/** Lua `host.translate`（manifest `offline_translate: true` 的插件才拿得到）。 */
class OfflineTranslateHostApiImpl(context: Context) : OfflineTranslateHostApi {
    private val app = context.applicationContext
    override fun status(from: String, to: String) = OfflineTranslation.status(app, from, to)
    override fun download(from: String, to: String) = OfflineTranslation.download(app, from, to)
    override fun translate(text: String, from: String, to: String, listener: OfflineTranslateListener) =
        OfflineTranslation.translate(app, text, from, to, listener)
    override fun cancel(id: Int) = OfflineTranslation.cancel(id)
}
