package com.kingzcheung.xime.settings

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import java.io.File

/** Settings/deployment-only adapter. No work is added to the per-keystroke candidate path. */
object CellDictionaryRime {
    private const val START = "# BEGIN CYIME CELL DICTIONARIES"
    private const val END = "# END CYIME CELL DICTIONARIES"
    private fun YamlMap.node(key: String): YamlNode? = entries.entries.firstOrNull { it.key.content == key }?.value

    fun supports(profile: InputProfile): Boolean = profile.language == InputLanguage.CHINESE &&
        profile.mode == InputMode.KEYBOARD && profile.engineProfile is EngineProfile.Rime &&
        profile.scheme in setOf(InputScheme.PINYIN, InputScheme.DOUBLE_PINYIN)

    internal fun packName(dictionary: String): String = "user_cyime_cells_" +
        CellDictionaryStore.digest(dictionary.toByteArray()).take(16)

    /** Add/remove only our marked append patch, retaining every existing pack and other setting. */
    internal fun patch(original: String, pack: String?): String {
        val beginCount = original.lineSequence().count { it.trim() == START }
        val endCount = original.lineSequence().count { it.trim() == END }
        require(beginCount == endCount && beginCount <= 1) { "词库配置标记损坏，请检查方案自定义文件" }
        val managed = Regex("(?m)^[ \\t]*${Regex.escape(START)}\\r?\\n[\\s\\S]*?^[ \\t]*${Regex.escape(END)}(?:\\r?\\n|$)")
        require(beginCount == 0 || managed.containsMatchIn(original)) { "词库配置标记顺序损坏" }
        val base = original.replace(managed, "")
        if (pack == null) return base
        require(pack.matches(Regex("user_cyime_cells_[a-f0-9]{16}")))
        val source = if (base.lineSequence().all { it.isBlank() || it.trimStart().startsWith('#') })
            base + (if (base.isEmpty() || base.endsWith('\n')) "" else "\n") + "patch:\n" else base
        val node = SchemaManager.yaml.parseToYamlNode(source)
        require(node is YamlMap || node is YamlNull) { "方案自定义文件不是 YAML 映射" }
        val root = node as? YamlMap
        val patchNode = root?.node("patch")
        val map = patchNode as? YamlMap
        require(map?.node("translator/packs/+") == null && map?.node("translator/packs/@next") == null) {
            "方案已自定义追加词库，暂不能自动合并；原配置已保留"
        }
        // Reject flow maps/aliases instead of rewriting third-party YAML and losing its semantics.
        val line = Regex("(?m)^patch:[ \\t]*(?:#[^\\r\\n]*)?(?:\\r?\\n|$)").find(source)
        require(line != null || patchNode == null) { "暂不支持此方案的 patch 写法；原配置已保留" }
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val indentation = if (map != null && map.entries.isNotEmpty()) {
            val following = source.substring(requireNotNull(line).range.last + 1)
            Regex("(?m)^([ ]+)\\S").find(following)?.groupValues?.get(1) ?: "  "
        } else "  "
        // user_* also satisfies the legacy T9 pack validator; the file already exists before deployment.
        val block = "$indentation$START$newline${indentation}\"translator/packs/+\": [\"$pack\"]$newline$indentation$END$newline"
        val output = if (line == null) {
            val withoutEnd = source.trimEnd().removeSuffix("...").trimEnd()
            "$withoutEnd${newline}patch:$newline$block"
        } else {
            val at = line.range.last + 1
            source.substring(0, at).trimEnd('\r', '\n') + newline + block + source.substring(at)
        }
        SchemaManager.yaml.parseToYamlNode(output) // validate before any write
        return output
    }

    private fun dictionary(schema: String, custom: String): String {
        val root = SchemaManager.yaml.parseToYamlNode(schema) as? YamlMap ?: error("无法读取方案")
        val base = (root.node("translator") as? YamlMap)?.node("dictionary") as? YamlScalar
        val customRoot = custom.takeIf { text -> text.lineSequence().any {
            it.isNotBlank() && !it.trimStart().startsWith('#')
        } }?.let { SchemaManager.yaml.parseToYamlNode(it) as? YamlMap }
        val patches = customRoot?.node("patch") as? YamlMap
        val value = patches?.node("translator/dictionary") as? YamlScalar
            ?: (patches?.node("translator") as? YamlMap)?.node("dictionary") as? YamlScalar ?: base
        return requireNotNull(value) { "方案没有可扩展的拼音词典" }.content.also {
            require(it.matches(Regex("[a-zA-Z0-9_/.-]+")) && !it.contains("..")) { "无效的方案词典名" }
        }
    }

    data class Binding(val schemaId: String, val pack: String)

    /** Plan all patches before writing anything, so an unsupported custom config cannot half-apply. */
    fun prepare(rimeDir: File, store: CellDictionaryStore, schemas: List<SchemaMeta>, ids: Set<String>): List<Binding> {
        val state = store.read()
        require(ids.all { id -> state.installed.any { it.id == id } && store.dictionaryFile(id).isFile }) {
            "已启用的词库文件缺失，请重新导入"
        }
        val writes = linkedMapOf<File, String>()
        val bindings = mutableListOf<Binding>()
        for (schema in schemas) {
            val customFile = File(rimeDir, "${schema.schemaId}.custom.yaml")
            val original = customFile.takeIf { it.isFile }?.readText().orEmpty()
            val supported = supports(schema.toSchemaInfo().profile)
            if (ids.isEmpty() || !supported) {
                if (original.contains(START)) writes[customFile] = patch(original, null)
                continue
            }
            val source = File(rimeDir, "${schema.schemaId}.schema.yaml").readText()
            val pack = packName(dictionary(source, original))
            bindings += Binding(schema.schemaId, pack)
            writes[customFile] = patch(original, pack)
            writes[File(rimeDir, "$pack.dict.yaml")] = buildString {
                append("---\nname: $pack\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\nimport_tables:\n")
                ids.sorted().forEach { append("  - cyime_cells/cell_$it\n") }
                append("...\n")
            }
        }
        ids.sorted().forEach { id ->
            writes[File(rimeDir, "cyime_cells/cell_$id.dict.yaml")] = store.dictionaryFile(id).readText()
        }
        writes.forEach { (file, text) -> CellDictionaryStore.writeAtomic(file, text) }
        return bindings
    }
}
