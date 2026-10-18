package com.kingzcheung.xime.model

import android.content.Context
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bridge downloaded resources to Rime's resolver without moving existing user resources. */
internal object CandidateModelAssets {
    private fun disabledKey(id: String) = "candidate_model_disabled_$id"
    private val verifiedRuntime = ConcurrentHashMap<String, List<String>>()

    fun setDisabled(context: Context, id: String, disabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(disabledKey(id), disabled).commit()
    }

    fun isDisabledAsset(context: Context, name: String): Boolean = CandidateModelCatalog.models.any { model ->
        model.files.any { it.name == name } &&
            SettingsPreferences.getPrefsPublic(context).getBoolean(disabledKey(model.id), false)
    }

    fun filterBundledManifest(context: Context, manifest: String): String = manifest.lineSequence()
        .filterNot { isDisabledAsset(context, it.split('\t').getOrNull(3).orEmpty()) }
        .joinToString("\n")

    fun existingDirectory(context: Context, model: ModelInfo): File? {
        if (model.category != ModelCategory.CANDIDATE) return null
        if (SettingsPreferences.getPrefsPublic(context).getBoolean(disabledKey(model.id), false)) return null
        val runtime = File(context.filesDir, "rime")
        return runtime.takeIf {
            model.versions.any { version ->
                if (!hasDownloadedModelFiles(runtime, version)) return@any false
                val key = "${runtime.absolutePath}/${model.id}/${version.version}"
                val signature = version.files.map { info ->
                    val file = modelDownloadFile(runtime, info.name)
                    "${info.name}:${info.sha256}:${file.length()}:${file.lastModified()}"
                }
                if (verifiedRuntime[key] == signature) return@any true
                runCatching { validateDownloadedModel(runtime, version) }.isSuccess.also { valid ->
                    if (valid) verifiedRuntime[key] = signature
                }
            }
        }
    }

    fun installAvailable(context: Context, models: List<ModelInfo> = CandidateModelCatalog.models) {
        for (model in models) {
            if (SettingsPreferences.getPrefsPublic(context).getBoolean(disabledKey(model.id), false)) continue
            val source = ModelStorage.getModelDir(context, model.id)
            val version = model.versions.firstOrNull { hasDownloadedModelFiles(source, it) } ?: continue
            // A user-owned conflicting resource must never prevent ordinary keyboard startup.
            runCatching { installCandidateModel(source, File(context.filesDir, "rime"), version) }
                .onFailure { com.kingzcheung.xime.util.FileLogger.w("CandidateModels", "候选增强未应用：${it.message}") }
        }
    }

    fun installDownloaded(context: Context, model: ModelInfo, version: ModelVersion) {
        // 用户主动安装时，同名文件若是目录里的已知内容（旧格式、或二选一的另一档），属于 CyIME 自己的文件，直接替换。
        installCandidateModel(ModelStorage.getModelDir(context, model.id), File(context.filesDir, "rime"), version) { name, file ->
            val owner = knownCatalogOwner(name, file) ?: return@installCandidateModel false
            if (owner.id != model.id) setDisabled(context, owner.id, true)
            true
        }
        setDisabled(context, model.id, false)
        if (model.id == CandidateModelCatalog.NGRAM || model.id == CandidateModelCatalog.NGRAM_SMALL) dropSupersededGrammar(context)
    }

    /**
     * 装了九键词组模型后，解码器用词组模型打分、不再加载语法增强，390 MB 白占空间：自动删掉。
     * 需要时可在商店重新下载（删掉词组模型后它会重新起作用）。
     */
    fun dropSupersededGrammar(context: Context) {
        val runtime = File(context.filesDir, "rime")
        val ngramInstalled = CandidateModelCatalog.models
            .filter { it.id == CandidateModelCatalog.NGRAM || it.id == CandidateModelCatalog.NGRAM_SMALL }
            .any { model -> model.versions.any { hasDownloadedModelFiles(runtime, it) } }
        if (!ngramInstalled) return
        val grammar = CandidateModelCatalog.models.firstOrNull { it.id == CandidateModelCatalog.GRAMMAR } ?: return
        val present = grammar.versions.flatMap { it.files }.any { info ->
            modelDownloadFile(runtime, info.name).exists() ||
                modelDownloadFile(ModelStorage.getModelDir(context, grammar.id), info.name).exists()
        }
        if (present && !ModelManager.deleteModel(context, grammar))
            com.kingzcheung.xime.util.FileLogger.w("CandidateModels", "语法增强已被词组模型取代，但未能删除")
    }

    private fun matchesAnyVersion(model: ModelInfo, name: String, file: File): Boolean = model.versions.any { version ->
        version.files.any { info -> info.name == name && info.sizeBytes == file.length() &&
            runCatching { verifyModelDigest(file, info.sha256) }.isSuccess }
    }

    private fun knownCatalogOwner(name: String, file: File): ModelInfo? =
        CandidateModelCatalog.models.firstOrNull { matchesAnyVersion(it, name, file) }

    /** Explicit deletion only removes known catalog content, never a custom same-name file. */
    fun removeRuntimeCopy(context: Context, model: ModelInfo): Boolean {
        val root = File(context.filesDir, "rime")
        // 任一已知版本（含旧格式）都算本条目的内容，才能删掉老版本留下的文件。
        val files = model.versions.flatMap { it.files }.map { it.name }.distinct().map { it to modelDownloadFile(root, it) }
        if (files.any { (name, file) -> file.exists() && !matchesAnyVersion(model, name, file) }) return false
        setDisabled(context, model.id, true)
        return files.all { (_, file) -> !file.exists() || file.delete() }
    }
}

/** Validate the complete source before publishing any file; preserve unknown destination content. */
internal fun installCandidateModel(
    source: File, runtime: File, version: ModelVersion,
    /** 已有同名但内容不同的文件可否替换；默认保留（启动时自动应用不替换任何文件）。 */
    canReplace: (name: String, existing: File) -> Boolean = { _, _ -> false },
): Boolean {
    // Installed hardlinks share exactly the already-verified downloaded bytes, without rehashing
    // hundreds of megabytes every time the settings or IME prepares existing resources.
    if (hasDownloadedModelFiles(source, version) && version.files.all { info ->
            runCatching { Files.isSameFile(modelDownloadFile(source, info.name).toPath(),
                modelDownloadFile(runtime, info.name).toPath()) }.getOrDefault(false)
        }) return false
    validateDownloadedModel(source, version)
    val replaced = mutableSetOf<String>()
    val missing = version.files.filter { info ->
        val existing = modelDownloadFile(runtime, info.name)
        if (existing.exists()) {
            try { verifyModelDigest(existing, info.sha256); false }
            catch (error: IOException) {
                if (!canReplace(info.name, existing))
                    throw IOException("保留已有不同版本的 ${info.name}，未覆盖；请先移走该文件后重试", error)
                replaced += info.name
                true
            }
        } else true
    }
    if (missing.isEmpty()) return false
    runtime.mkdirs()
    val pending = mutableListOf<Pair<File, File>>()
    try {
        // Publish the vocabulary before ONNX so an active resolver never sees a model without it.
        for (info in missing.sortedBy { it.name.endsWith(".onnx") }) {
            val input = findModelFile(source, version, info.name)
            val destination = modelDownloadFile(runtime, info.name)
            val temporary = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.installing")
            pending += temporary to destination
            try { Files.createLink(temporary.toPath(), input.toPath()) }
            catch (_: Exception) { input.copyTo(temporary, overwrite = true) }
            verifyModelDigest(temporary, info.sha256)
        }
        for ((temporary, destination) in pending) {
            // 新文件已全部校验通过，才移走要替换的旧文件。
            if (destination.name in replaced && destination.exists() && !destination.delete())
                throw IOException("无法替换旧的 ${destination.name}")
            // A link publishes atomically without replacing a destination created after preflight.
            try { Files.createLink(destination.toPath(), temporary.toPath()) }
            catch (exists: FileAlreadyExistsException) { throw exists }
            catch (_: UnsupportedOperationException) { Files.move(temporary.toPath(), destination.toPath()) }
            catch (error: IOException) {
                if (destination.exists()) throw error
                // No REPLACE_EXISTING / ATOMIC_MOVE: preserve competing files on linkless storage.
                Files.move(temporary.toPath(), destination.toPath())
            }
        }
        return true
    } finally { pending.forEach { it.first.delete() } }
}
