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
        installCandidateModel(ModelStorage.getModelDir(context, model.id), File(context.filesDir, "rime"), version)
        setDisabled(context, model.id, false)
    }

    /** Explicit deletion only removes known catalog content, never a custom same-name file. */
    fun removeRuntimeCopy(context: Context, model: ModelInfo): Boolean {
        val root = File(context.filesDir, "rime")
        val files = model.files.map { info -> info to modelDownloadFile(root, info.name) }
        try {
            files.filter { it.second.exists() }.forEach { (info, file) -> verifyModelDigest(file, info.sha256) }
        } catch (_: IOException) { return false }
        setDisabled(context, model.id, true)
        return files.all { (_, file) -> !file.exists() || file.delete() }
    }
}

/** Validate the complete source before publishing any file; preserve unknown destination content. */
internal fun installCandidateModel(source: File, runtime: File, version: ModelVersion): Boolean {
    // Installed hardlinks share exactly the already-verified downloaded bytes, without rehashing
    // hundreds of megabytes every time the settings or IME prepares existing resources.
    if (hasDownloadedModelFiles(source, version) && version.files.all { info ->
            runCatching { Files.isSameFile(modelDownloadFile(source, info.name).toPath(),
                modelDownloadFile(runtime, info.name).toPath()) }.getOrDefault(false)
        }) return false
    validateDownloadedModel(source, version)
    val missing = version.files.filter { info ->
        val existing = modelDownloadFile(runtime, info.name)
        if (existing.exists()) {
            try { verifyModelDigest(existing, info.sha256) }
            catch (error: IOException) { throw IOException("保留已有不同版本的 ${info.name}，未覆盖；请先移走该文件后重试", error) }
            false
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
