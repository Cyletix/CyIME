package com.kingzcheung.xime.speech

import android.content.Context
import com.kingzcheung.xime.model.ModelCategory
import com.kingzcheung.xime.model.ModelManager
import com.kingzcheung.xime.model.ModelStorage
import java.io.File

/**
 * ASR 模型管理与选择。
 *
 * 使用官方 sherpa-onnx；保留原市场 Zipformer，补充 ASRInput 模型和双模型选择。
 * 模型清单与描述来自「扩展商店」远程索引（[ModelManager]，category=asr），
 * 新语音模型也有本地固定索引；索引离线时保留 Zipformer 兼容入口。
 */
class AsrModelManager(private val context: Context) {

    companion object {
        /** 内置默认 ASR 模型（远程索引加载前/失败时的兜底）。 */
        val DEFAULT_MODEL = AsrModelInfo(
            id = "zipformer-zh-int8",
            name = "中文 Zipformer int8",
            description = "Zipformer 架构，适合实时语音识别，int8 量化",
            language = "zh",
            size = "132.63MB",
            downloadUrl = "https://www.modelscope.cn/models/bikeand/asr/resolve/master/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2",
            modelType = "transducer",
            files = listOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt"),
            encoderFile = "encoder.int8.onnx",
            decoderFile = "decoder.onnx",
            joinerFile = "joiner.int8.onnx"
        )

        /** 把索引里的 ModelInfo 转换为 ASR 专用模型信息。 */
        private fun toAsrModelInfo(info: com.kingzcheung.xime.model.ModelInfo): AsrModelInfo {
            val version = info.resolvedVersion()
            val fileNames = info.files.map { it.name }
            return AsrModelInfo(
                id = info.id,
                name = info.name,
                description = info.description,
                language = when (info.id) { SpeechModelCatalog.SENSEVOICE -> "auto"; SpeechModelCatalog.PARAFORMER -> "zh-en"; else -> "zh" },
                size = version?.size ?: info.size,
                downloadUrl = info.archiveUrl ?: "",
                modelType = when (info.id) { SpeechModelCatalog.PARAFORMER -> "paraformer"; SpeechModelCatalog.SENSEVOICE -> "sensevoice"; else -> "transducer" },
                files = fileNames,
                encoderFile = "encoder.int8.onnx",
                decoderFile = if (info.id == SpeechModelCatalog.PARAFORMER) "decoder.int8.onnx" else "decoder.onnx",
                joinerFile = "joiner.int8.onnx"
            )
        }
    }

    data class AsrModelInfo(
        val id: String,
        val name: String,
        val description: String = "",
        val language: String,
        val size: String,
        val downloadUrl: String,
        val modelType: String = "transducer",
        val files: List<String>,
        val encoderFile: String = "",
        val decoderFile: String = "",
        val joinerFile: String = ""
    )

    /** ASR 分类的模型清单（索引优先，索引未加载时用内置默认）。 */
    fun getAsrModels(): List<AsrModelInfo> {
        val fromIndex = ModelManager.getModelsByCategory(ModelCategory.ASR)
            .map { toAsrModelInfo(it) }
        return (fromIndex + SpeechModelCatalog.models.map(::toAsrModelInfo) + DEFAULT_MODEL)
            .distinctBy { it.id }.sortedBy { listOf(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE).indexOf(it.id).let { order -> if (order < 0) Int.MAX_VALUE else order } }
    }

    fun isModelReady(): Boolean = try { selection().ready } catch (_: Exception) { false }

    data class Selection(
        val mode: String,
        val first: AsrModelInfo?, val firstDir: File?, val secondDir: File?,
    ) {
        val ready: Boolean get() = (first == null || firstDir != null && first.files.isNotEmpty() && first.files.all { File(firstDir, it).let { f -> f.isFile && f.length() > 0 } }) &&
            (secondDir == null || listOf("model.int8.onnx", "tokens.txt").all { File(secondDir, it).let { f -> f.isFile && f.length() > 0 } })
        val key: String get() = listOfNotNull(firstDir, secondDir).joinToString(prefix = "$mode:") { dir ->
            dir.absolutePath + ":" + dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
                .joinToString { "${it.name}:${it.length()}:${it.lastModified()}" }
        }
    }

    fun selection(mode: String = getSelectedModelId()): Selection {
        fun dir(id: String): File {
            ModelStorage.migrateLegacyForModel(context, id)
            return ModelStorage.getModelDir(context, id)
        }
        val firstId = when (mode) {
            SpeechModelCatalog.TWO_PASS -> SpeechModelCatalog.PARAFORMER
            SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.ZIPFORMER
            else -> null
        }
        if (firstId != null) return Selection(mode,
            getAsrModels().first { it.id == firstId }, dir(firstId), dir(SpeechModelCatalog.SENSEVOICE))
        val info = getAsrModels().firstOrNull { it.id == mode } ?: error("不支持的语音模型：$mode")
        return if (info.modelType == "sensevoice") Selection(mode, null, null, dir(mode))
        else Selection(mode, info, dir(mode), null)
    }

    private val preferences get() = context.getSharedPreferences("asr_model", Context.MODE_PRIVATE)

    /** Persist the complete selection as one value, also passed explicitly to the :asr process. */
    fun getSelectedModelId(): String {
        val stored = preferences.getString("selected_model", SpeechModelCatalog.ZIPFORMER) ?: SpeechModelCatalog.ZIPFORMER
        if (stored != SpeechModelCatalog.SENSEVOICE) return stored
        // Migrate the retired standalone choice without downloading or pretending missing weights are ready.
        val base = if (selection(SpeechModelCatalog.PARAFORMER).ready) SpeechModelCatalog.PARAFORMER else SpeechModelCatalog.ZIPFORMER
        val migrated = combinedMode(base, true)
        setModel(migrated)
        return migrated
    }

    fun getFirstPassModelId(): String = when (val mode = getSelectedModelId()) {
        SpeechModelCatalog.TWO_PASS -> SpeechModelCatalog.PARAFORMER
        SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.ZIPFORMER
        else -> mode
    }

    fun isRefinementEnabled(): Boolean = getSelectedModelId() in setOf(
        SpeechModelCatalog.TWO_PASS, SpeechModelCatalog.ZIPFORMER_TWO_PASS)

    fun setFirstPassModel(modelId: String) {
        require(modelId in listOf(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.PARAFORMER))
        setModel(combinedMode(modelId, isRefinementEnabled()))
    }

    fun setRefinementEnabled(enabled: Boolean) = setModel(combinedMode(getFirstPassModelId(), enabled))

    private fun combinedMode(base: String, refine: Boolean): String = if (!refine) base
        else if (base == SpeechModelCatalog.PARAFORMER) SpeechModelCatalog.TWO_PASS else SpeechModelCatalog.ZIPFORMER_TWO_PASS

    fun setModel(modelId: String) {
        preferences.edit().putString("selected_model", modelId).apply()
    }
}
