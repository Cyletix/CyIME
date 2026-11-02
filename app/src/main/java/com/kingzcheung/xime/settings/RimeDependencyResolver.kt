package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlScalar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 一个方案文件里引用到的、可能需要补齐的依赖（文件级 + 包级）。 */
data class SchemaRefs(
    val mainDicts: Set<String> = emptySet(),     // translator.dictionary + dict 的 import_tables
    val reverseDicts: Set<String> = emptySet(),  // reverse_lookup.dictionary
    val presets: Set<String> = emptySet(),       // import_preset: <p>  → <p>.yaml
    val needsEssay: Boolean = false,             // use_preset_vocabulary: true → essay.txt
    val schemaDeps: Set<String> = emptySet(),    // schema 内 dependencies:（包 id，用于递归）
    val grammarModels: Set<String> = emptySet(), // grammar.language 是模型名，与输入语言无关
)

/** [RimeDependencyResolver.complete] 的结果。绝不抛异常；失败项都落进对应列表。 */
data class CompletionResult(
    val downloaded: List<String> = emptyList(),        // 成功下载+解压的包 id
    val failedToDownload: List<String> = emptyList(),  // 有 URL 但下载/解压失败
    val unresolved: List<String> = emptyList(),        // resolveUrl 返回 null，无从下载
    val stillMissingFiles: List<String> = emptyList(), // 补完后仍缺的文件级依赖
    val alreadyPresent: List<String> = emptyList(),    // 本地（上游/app 自带或先前已装）已具备，跳过下载
)

internal data class DownloadOutcome(
    val downloaded: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val unresolved: List<String> = emptyList(),
    val alreadyPresent: List<String> = emptyList(),
)

/**
 * 「按依赖补齐」引擎：给定一个已下载方案 + 它声明的依赖（包 id）+ 一个 id→下载URL 的解析函数，
 * 递归下载并解压所需依赖包，让方案带反查完整编译；并如实返回补完后仍缺的文件。
 * 不剥离方案任何内容；离线/失败时优雅降级。"哪个包从哪下"的知识留在调用方（市场层/索引）。
 */
object RimeDependencyResolver {
    private const val TAG = "RimeDependencyResolver"

    /** 扫描方案(.schema.yaml) + 可选词典(.dict.yaml) 文本，列出其引用到的依赖。纯函数。 */
    fun scanSchemaRefs(schemaYaml: String, dictYaml: String?): SchemaRefs {
        val mainDicts = linkedSetOf<String>()
        val reverseDicts = linkedSetOf<String>()
        val presets = linkedSetOf<String>()
        val schemaDeps = linkedSetOf<String>()
        val grammarModels = linkedSetOf<String>()
        var needsEssay = false

        for (text in listOf(schemaYaml, dictYaml ?: "")) {
            var top = ""
            var listTarget: MutableSet<String>? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line == "...") break // Rime 词典的 YAML 头结束，下面是制表符分隔的词条。
                if (line.isEmpty() || line.startsWith("#")) continue
                val indent = raw.takeWhile { it == ' ' }.length

                if (line.startsWith("- ")) {
                    listTarget?.addAll(referenceValues(line.removePrefix("- ")))
                    continue
                }
                listTarget = null
                if (indent == 0) top = line.substringBefore(":").trim()

                when {
                    line.startsWith("dictionary:") -> {
                        (if (top == "reverse_lookup") reverseDicts else mainDicts)
                            .addAll(referenceValues(line.substringAfter(':')))
                    }
                    line.startsWith("import_preset:") -> {
                        presets.addAll(referenceValues(line.substringAfter(':')))
                    }
                    line.startsWith("dependencies:") && top == "schema" -> listTarget = schemaDeps
                    line.startsWith("language:") && top == "grammar" ->
                        grammarModels.addAll(referenceValues(line.substringAfter(':')))
                    line.startsWith("import_tables:") -> listTarget = mainDicts
                    line.startsWith("use_preset_vocabulary:") ->
                        if (line.substringAfter(":").trim().startsWith("true")) needsEssay = true
                }
                listTarget?.addAll(referenceValues(line.substringAfter(':')))
            }
        }
        return SchemaRefs(mainDicts, reverseDicts, presets, needsEssay, schemaDeps, grammarModels)
    }

    // 只解析引用值，交给 YAML 处理引号、行内列表和注释，避免把薄荷等方案的说明当成文件名。
    private fun referenceValues(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val node = runCatching { SchemaManager.yaml.parseToYamlNode(value.trim()) }.getOrNull()
        val values = when (node) {
            is YamlScalar -> listOf(node.content)
            is YamlList -> node.items.mapNotNull { (it as? YamlScalar)?.content }
            else -> emptyList()
        }
        return values.filter { it.isNotBlank() }
    }

    /** 把 [SchemaRefs] 推成期望存在的文件名集合。纯函数。 */
    fun expectedFiles(refs: SchemaRefs): Set<String> {
        val s = linkedSetOf<String>()
        refs.mainDicts.forEach { s.add("$it.dict.yaml") }
        refs.reverseDicts.forEach { s.add("$it.dict.yaml") }
        refs.presets.forEach { s.add("$it.yaml") }
        refs.grammarModels.forEach { s.add("$it.gram") }
        if (refs.needsEssay) s.add("essay.txt")
        return s
    }

    /** 本地导入只检查方案直接声明的文件，允许用户随后补齐，不联网也不读取整份词库。 */
    internal fun missingImportFiles(dir: File, schemaId: String): List<String> {
        val file = File(dir, "$schemaId.schema.yaml")
        val refs = try {
            scanSchemaRefs(file.readText(), null)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Cannot check references in ${file.name}", e)
            return emptyList()
        }
        val files = expectedFiles(refs) + refs.schemaDeps.map { "$it.schema.yaml" }
        return files.filterNot { name ->
            val compiled = if (name.endsWith(".dict.yaml"))
                name.removeSuffix(".dict.yaml") + ".table.bin" else name
            listOf(name, compiled, "build/$compiled").any { path ->
                runCatching { SchemaManifestManager.safeTarget(dir, path).isFile }.getOrDefault(false)
            }
        }
    }

    /**
     * 依赖图递归下载（纯编排，注入下载/解析/本地校验函数，便于单测）。
     * **本地优先**：[isLocallySatisfied] 为真的依赖（上游/app 已自带或先前已装）跳过下载，
     * 但仍跟进其传递依赖；正常情况下 symbols/default 这类基础文件由上游满足、不重复下发。
     */
    internal suspend fun completeCore(
        topDeps: List<String>,
        resolveUrl: (String) -> String?,
        download: suspend (String) -> Boolean,
        readPkgDeps: (String) -> List<String>,
        isLocallySatisfied: (String) -> Boolean = { false },
    ): DownloadOutcome {
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(topDeps)
        val downloaded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        val alreadyPresent = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            // 本地优先：已具备则不下载（但仍跟进其传递依赖，以防传递项缺失）
            if (isLocallySatisfied(id)) {
                alreadyPresent.add(id)
                for (dep in readPkgDeps(id)) if (dep !in seen) queue.addLast(dep)
                continue
            }
            val url = resolveUrl(id)
            if (url == null) {
                unresolved.add(id)
                continue
            }
            if (download(url)) {
                downloaded.add(id)
                for (dep in readPkgDeps(id)) if (dep !in seen) queue.addLast(dep)
            } else {
                failed.add(id)
            }
        }
        return DownloadOutcome(downloaded, failed, unresolved, alreadyPresent)
    }

    /**
     * 依赖是否已由本地提供（上游/app 自带，或先前安装）：
     * 词典类 `<id>.dict.yaml` 或 preset 类 `<id>.yaml` 存在即视为已具备。纯函数（注入目录）。
     */
    fun isDependencyLocallyPresent(rimeDir: File, id: String): Boolean =
        File(rimeDir, "$id.dict.yaml").exists() || File(rimeDir, "$id.yaml").exists()

    /**
     * 公开入口：按 [dependencies] 递归补齐 [schemaId] 所需依赖包，返回结果。
     * @param resolveUrl 由调用方（市场层）提供：包 id → 下载 URL（来自 xime-index）。
     */
    suspend fun complete(
        context: Context,
        schemaId: String,
        dependencies: List<String>,
        resolveUrl: (String) -> String?,
    ): CompletionResult = withContext(Dispatchers.IO) {
        val rimeDir = SchemaManager.getRimeDir(context)
        val outcome = completeCore(
            topDeps = dependencies,
            resolveUrl = resolveUrl,
            download = { url -> SchemaManager.importFromUrl(context, url) },
            readPkgDeps = { id -> readSchemaDependencies(context, id) },
            // 本地优先：上游/app 已带（如 symbols、default）或先前已装的依赖不重复下载
            isLocallySatisfied = { id -> isDependencyLocallyPresent(rimeDir, id) },
        )
        val stillMissing = findMissingFiles(context, schemaId)
        if (outcome.unresolved.isNotEmpty() || stillMissing.isNotEmpty()) {
            Log.w(TAG, "complete($schemaId): unresolved=${outcome.unresolved}, stillMissing=$stillMissing, present=${outcome.alreadyPresent}")
        }
        CompletionResult(
            downloaded = outcome.downloaded,
            failedToDownload = outcome.failed,
            unresolved = outcome.unresolved,
            stillMissingFiles = stillMissing,
            alreadyPresent = outcome.alreadyPresent,
        )
    }

    /** 读 `<id>.schema.yaml` 的 dependencies 块（用于递归发现传递依赖）。 */
    private fun readSchemaDependencies(context: Context, id: String): List<String> {
        val f = File(SchemaManager.getRimeDir(context), "$id.schema.yaml")
        if (!f.exists()) return emptyList()
        return try {
            scanSchemaRefs(f.readText(), null).schemaDeps.toList()
        } catch (e: Exception) {
            Log.e(TAG, "readSchemaDependencies($id) failed", e)
            emptyList()
        }
    }

    /** 扫描 [schemaId] 引用、过滤本地已存在文件，返回仍缺的文件名。 */
    fun findMissingFiles(context: Context, schemaId: String): List<String> {
        val dir = SchemaManager.getRimeDir(context)
        val schemaFile = File(dir, "$schemaId.schema.yaml")
        if (!schemaFile.exists()) return emptyList()
        return try {
            val schemaText = schemaFile.readText()
            // 主词典名以扫描器为准（getReferencedDictName 取首个 dictionary，块顺序反时会误取反查词典）
            val mainDict = scanSchemaRefs(schemaText, null).mainDicts.firstOrNull() ?: schemaId
            val dictFile = File(dir, "$mainDict.dict.yaml")
            val refs = scanSchemaRefs(schemaText, dictFile.takeIf { it.exists() }?.readText())
            expectedFiles(refs).filter { !File(dir, it).exists() }
        } catch (e: Exception) {
            Log.e(TAG, "findMissingFiles($schemaId) failed", e)
            emptyList()
        }
    }
}
